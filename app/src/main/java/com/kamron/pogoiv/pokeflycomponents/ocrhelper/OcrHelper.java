package com.kamron.pogoiv.pokeflycomponents.ocrhelper;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;
import android.util.LruCache;

import com.google.android.gms.tasks.Tasks;
import com.google.common.base.Optional;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.kamron.pogoiv.GoIVSettings;
import com.kamron.pogoiv.Pokefly;
import com.kamron.pogoiv.scanlogic.Data;
import com.kamron.pogoiv.scanlogic.PokeInfoCalculator;
import com.kamron.pogoiv.scanlogic.Pokemon;
import com.kamron.pogoiv.scanlogic.ScanData;
import com.kamron.pogoiv.utils.GUIColorFromPokeType;
import com.kamron.pogoiv.utils.LevelRange;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

import static com.kamron.pogoiv.pokeflycomponents.ocrhelper.ScanFieldNames.ARC_INIT_POINT;
import static com.kamron.pogoiv.pokeflycomponents.ocrhelper.ScanFieldNames.ARC_RADIUS;


/**
 * Scans a screenshot and extracts pokemon info using ML Kit's bundled (offline)
 * on-device text recognition. Replaces the old Tesseract-based implementation.
 */
public class OcrHelper {

    private static OcrHelper instance = null;
    private static TextRecognizer textRecognizer = null;
    private static boolean isPokeSpamEnabled;
    private static LruCache<String, String> ocrCache;

    private static int DEFAULT_FONT_COLOR = 4680814; //Pokemon go font color, rgb 71,108,110 (approximate)
    private static int CANT_AFFORD_FONT_COLOR = 16736100; //Pokemon go font color, rgb 255,95,100 (approximate)

    private static WeakReference<Pokefly> pokeflyRef;


    private OcrHelper() {
    }

    /**
     * Initializes the OCR helper and readies it for use. Uses ML Kit's bundled text
     * recognition model, which works fully offline (no Play Services needed).
     *
     * @param pokefly             the calling service, used for settings and resources
     * @param pokeInfoCalculator  the IV calculator instance
     * @return the shared OcrHelper instance
     */
    public static synchronized OcrHelper init(@NonNull Pokefly pokefly,
                                              @NonNull PokeInfoCalculator pokeInfoCalculator) {
        pokeflyRef = new WeakReference<>(pokefly);

        if (instance == null) {
            // Bundled model: ships inside the APK, works offline with no Google Play Services.
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            ocrCache = new LruCache<>(200);
            instance = new OcrHelper();
        }

        // Reload current settings
        GoIVSettings settings = GoIVSettings.getInstance(pokefly);
        isPokeSpamEnabled = settings.isPokeSpamEnabled();

        return instance;
    }

    public void exit() {
        if (textRecognizer != null) {
            textRecognizer.close();
            textRecognizer = null;
        }
        instance = null;
        ocrCache = null;
    }

    /**
     * A single recognized line of text plus its bounding box, expressed as fractions
     * of the source image dimensions for resolution independence.
     */
    private static class LineInfo {
        final String text;
        final float centerX; // 0..1
        final float centerY; // 0..1

        LineInfo(String text, Rect box, int imgW, int imgH) {
            this.text = text;
            this.centerX = (box.left + box.right) / 2f / imgW;
            this.centerY = (box.top + box.bottom) / 2f / imgH;
        }
    }

    /**
     * Runs ML Kit text recognition over the whole screenshot. Must be called off the
     * main thread (Pokefly dispatches scans to a background executor).
     *
     * @param pokemonImage the screenshot bitmap
     * @return recognized lines with their positions, or an empty list on failure
     */
    private static List<LineInfo> recognizeLines(@NonNull Bitmap pokemonImage) {
        List<LineInfo> lines = new ArrayList<>();
        try {
            InputImage image = InputImage.fromBitmap(pokemonImage, 0);
            Text result = Tasks.await(textRecognizer.process(image), 15, TimeUnit.SECONDS);
            int w = pokemonImage.getWidth();
            int h = pokemonImage.getHeight();
            for (Text.TextBlock block : result.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    Rect box = line.getBoundingBox();
                    String text = line.getText();
                    if (box != null && text != null && !text.trim().isEmpty()) {
                        lines.add(new LineInfo(text.trim(), box, w, h));
                    }
                }
            }
        } catch (Exception e) {
            Timber.w(e, "ML Kit text recognition failed");
        }
        return lines;
    }

    /**
     * Finds the line whose vertical center falls inside [yMin, yMax] and whose text
     * matches the given pattern. When several match, the one closest to the band
     * center wins.
     */
    @Nullable
    private static LineInfo findLineInBand(List<LineInfo> lines, float yMin, float yMax,
                                           java.util.regex.Pattern pattern) {
        LineInfo best = null;
        float bestDist = Float.MAX_VALUE;
        float mid = (yMin + yMax) / 2f;
        for (LineInfo line : lines) {
            if (line.centerY >= yMin && line.centerY <= yMax
                    && pattern.matcher(line.text).find()) {
                float dist = Math.abs(line.centerY - mid);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = line;
                }
            }
        }
        return best;
    }

    private static final java.util.regex.Pattern DIGITS = java.util.regex.Pattern.compile("\\d+");
    private static final java.util.regex.Pattern HP_PATTERN =
            java.util.regex.Pattern.compile("\\d+\\s*/\\s*\\d+");
    private static final java.util.regex.Pattern LETTERS =
            java.util.regex.Pattern.compile("[A-Za-z\u2640\u2642]{2,}");

    private static Optional<Integer> parseFirstInt(String raw) {
        try {
            String fixed = fixOcrLettersToNums(raw);
            java.util.regex.Matcher m = DIGITS.matcher(fixed);
            if (m.find()) {
                return Optional.of(Integer.parseInt(m.group()));
            }
        } catch (NumberFormatException e) {
            Timber.d(e);
        }
        return Optional.absent();
    }

    /**
     * Scans a screenshot and extracts the visible pokemon information.
     *
     * Field locations are matched by relative screen bands (top-to-bottom layout of
     * the pokemon detail screen) instead of the old fixed-pixel ScanAreas, so no
     * per-device calibration is needed.
     *
     * @param settings       user settings (kept for interface stability)
     * @param pokemonImage   screenshot of the pokemon detail screen
     * @param trainerLevel   the trainer's level, used for level estimation
     * @param requestFullScan whether to also read candy amount (PokeSpam)
     * @return ScanData with everything the recognizer found (absent fields left empty)
     */
    public ScanData scanPokemon(@NonNull GoIVSettings settings,
                                @NonNull Bitmap pokemonImage,
                                int trainerLevel,
                                boolean requestFullScan) {

        if (requestFullScan) {
            rememberGUIAccentColorBasedOnScan(pokemonImage);
        }
        ensureCorrectLevelArcSettings(settings, trainerLevel);

        List<LineInfo> lines = recognizeLines(pokemonImage);

        /* Scan fields top to bottom using relative screen bands. */

        // CP: big number near the top of the screen.
        Optional<Integer> cp = Optional.absent();
        LineInfo cpLine = findLineInBand(lines, 0.02f, 0.20f, DIGITS);
        if (cpLine != null) {
            cp = parseFirstInt(cpLine.text);
        }

        // Name: lettered line below the CP. Gender symbols may trail the name.
        // Band measured from a real 1080x2400 capture: the name renders at ~38% of
        // screen height on tall phones (the old 10-30% band sat above it and the
        // species guesser starved). The HP line (~45%) also matches LETTERS via
        // "HP", but the name sits far closer to this band's center and wins.
        String name = "";
        Pokemon.Gender gender = Pokemon.Gender.N;
        LineInfo nameLine = findLineInBand(lines, 0.30f, 0.46f, LETTERS);
        if (nameLine != null) {
            String rawName = nameLine.text;
            if (rawName.contains("\u2640")) {
                gender = Pokemon.Gender.F;
            } else if (rawName.contains("\u2642")) {
                gender = Pokemon.Gender.M;
            }
            name = fixOcrNumsToLetters(rawName.replaceAll("[\u2640\u2642]", "").replace(" ", ""));
        }

        // HP: "current / max" line.
        Optional<Integer> hp = Optional.absent();
        LineInfo hpLine = findLineInBand(lines, 0.25f, 0.48f, HP_PATTERN);
        if (hpLine != null) {
            String[] parts = hpLine.text.split("/");
            if (parts.length == 2) {
                hp = parseFirstInt(parts[1]); // max HP
                if (!hp.isPresent()) {
                    hp = parseFirstInt(parts[0]);
                }
            }
        }

        // Type: e.g. "WATER / FLYING", below the HP bar.
        String type = "";
        LineInfo typeLine = findLineInBand(lines, 0.44f, 0.58f,
                java.util.regex.Pattern.compile("[A-Za-z]{3,}(\\s*/\\s*[A-Za-z]{3,})?"));
        if (typeLine != null && (name.isEmpty() || !typeLine.text.equalsIgnoreCase(name))) {
            type = typeLine.text;
        }

        // Candy names: lines ending in "CANDY".
        List<String> candyNames = new ArrayList<>(Arrays.asList("", ""));
        java.util.regex.Pattern candyPattern =
                java.util.regex.Pattern.compile("(?i)([A-Z\u2640\u2642 .'-]+)\\s+CANDY");
        int candyIdx = 0;
        for (LineInfo line : lines) {
            if (line.centerY >= 0.40f && line.centerY <= 0.70f && candyIdx < 2) {
                java.util.regex.Matcher m = candyPattern.matcher(line.text);
                if (m.find()) {
                    candyNames.set(candyIdx, m.group(1).trim());
                    candyIdx++;
                }
            }
        }

        // Candy amount (PokeSpam): number next to the candy name.
        Optional<Integer> candyAmount = Optional.absent();
        if (requestFullScan && isPokeSpamEnabled) {
            LineInfo candyLine = findLineInBand(lines, 0.40f, 0.70f,
                    java.util.regex.Pattern.compile("(?i)CANDY\\s*x?\\s*\\d+|\\d+\\s*x?\\s*(?i)CANDY"));
            if (candyLine != null) {
                candyAmount = parseFirstInt(candyLine.text);
            }
        }

        // Power-up costs: numbers in the lower-middle band. Stardust costs are large
        // (>= 1000-ish for leveled pokemon), candy costs are small.
        Optional<Integer> powerUpCandyCost = Optional.absent();
        Optional<Integer> powerUpStardustCost = Optional.absent();
        List<Integer> costNumbers = new ArrayList<>();
        for (LineInfo line : lines) {
            if (line.centerY >= 0.55f && line.centerY <= 0.85f) {
                java.util.regex.Matcher m = DIGITS.matcher(fixOcrLettersToNums(line.text));
                while (m.find()) {
                    try {
                        costNumbers.add(Integer.parseInt(m.group()));
                    } catch (NumberFormatException e) {
                        Timber.d(e);
                    }
                }
            }
        }
        for (int cost : costNumbers) {
            if (cost >= 200 && !powerUpStardustCost.isPresent()) {
                powerUpStardustCost = Optional.of(cost);
            } else if (cost < 200 && !powerUpCandyCost.isPresent()) {
                powerUpCandyCost = Optional.of(cost);
            }
        }

        // Evolution cost: number near an "EVOLVE" label; fall back to small cost numbers.
        Optional<Integer> evolutionCost = Optional.absent();
        for (LineInfo line : lines) {
            if (line.centerY >= 0.60f && line.centerY <= 0.95f
                    && line.text.toUpperCase().contains("EVOLVE")) {
                evolutionCost = parseFirstInt(line.text);
                if (evolutionCost.isPresent()) {
                    break;
                }
            }
        }

        // Level: arc pixel scan, refined with the power-up candy cost mapping.
        double estimatedPokemonLevel;
        try {
            estimatedPokemonLevel = getPokemonLevelFromImg(pokemonImage, trainerLevel);
        } catch (RuntimeException e) {
            // Never let a level-scan hiccup fail the whole scan: fall back to the
            // max-level estimate; the candy-cost refinement below still narrows it.
            estimatedPokemonLevel = Data.trainerLevelToMaxPokeLevel(trainerLevel);
        }
        LevelRange estimatedLevelRange =
                refineLevelEstimate(trainerLevel, powerUpCandyCost, estimatedPokemonLevel);

        String uniqueIdentifier = name + type
                + (candyNames.size() > 0 ? candyNames.get(0) : "")
                + (candyNames.size() > 1 ? candyNames.get(1) : "")
                + hp.toString() + cp.toString()
                + powerUpStardustCost.toString() + powerUpCandyCost.toString();

        return new ScanData(estimatedLevelRange, name, type, candyNames, gender, hp, cp, candyAmount,
                evolutionCost, powerUpStardustCost, powerUpCandyCost, null, null, false, uniqueIdentifier);
    }

    private static double getPokemonLevelFromImg(@NonNull Bitmap pokemonImage, int trainerLevel) {
        double estimatedPokemonLevel = Data.trainerLevelToMaxPokeLevel(trainerLevel);
        double previousEstPokemonLevel = estimatedPokemonLevel + 0.5; // Initial value out of range
        int previousLevelDistance = -1; // Initial value indicating no found white pixels
        for (double estPokemonLevel = estimatedPokemonLevel; estPokemonLevel >= 1.0; estPokemonLevel -= 0.5) {
            int index = Data.levelToLevelIdx(estPokemonLevel);
            int x = Data.arcX[index];
            int y = Data.arcY[index];
            int whiteLineDistance = getCardinalWhiteLineDistFromImg(pokemonImage, x, y);

            // If we found a lower white line distance than our last calculation, last calculation was best match.
            // If the actual level is 1.0, we fall out to the default case below the for loop.
            if (whiteLineDistance < previousLevelDistance) {
                return previousEstPokemonLevel;
            }

            // Have not passed the best match yet; store current values for next loop cycle
            previousEstPokemonLevel = estPokemonLevel;
            previousLevelDistance = whiteLineDistance;
        }
        return 1;
    }

    /**
     * Examines the image from the given coordinates to determine the distance which is consistently white pixels in
     * 2 directions: towards and away the level arc center.
     * This helps identify the point closest to the center of the level indicator dot.
     *
     * @param pokemonImage The image of the entire screen
     * @param x            Horizontal ordinate to scan from
     * @param y            Vertical ordinate to scan from
     * @return -1 if the given coordinate is not a white pixel, otherwise the distance from given
     * coordinate which is white in each cardinal direction.
     */
    private static int getCardinalWhiteLineDistFromImg(Bitmap pokemonImage, int x, int y) {
        final int targetColor = Color.WHITE;

        // The arc endpoint tables can land outside the bitmap on tall screens:
        // the arc radius is height-based, so its far ends overhang the width.
        // An out-of-bounds start point is simply "not a white pixel" — the
        // in-loop guard below only runs after the first read, so check here.
        if (x < 0 || x >= pokemonImage.getWidth() || y < 0 || y >= pokemonImage.getHeight()) {
            return -1;
        }

        Double angle = null;
        int r = -1;
        int i1x = x;
        int i1y = y;
        int i2x = x;
        int i2y = y;

        while (pokemonImage.getPixel(i1x, i1y) == targetColor || pokemonImage.getPixel(i2x, i2y) == targetColor) {
            r++;
            if (angle == null) {
                angle = Math.atan2(Data.arcInitY - y, Data.arcInitX - x);
            }
            i1x = (int) Math.round(x + r * Math.cos(angle));
            i1y = (int) Math.round(y + r * Math.sin(angle));
            i2x = (int) Math.round(x - r * Math.cos(angle));
            i2y = (int) Math.round(y - r * Math.sin(angle));
            if (i1x < 0 || i1x >= pokemonImage.getWidth()
                    || i1y < 0 || i1y >= pokemonImage.getHeight()
                    || i2x < 0 || i2x >= pokemonImage.getWidth()
                    || i2y < 0 || i2y >= pokemonImage.getHeight()) {
                return -1;
            }
        }

        return r;
    }

    /**
     * Get the evolution cost for a pokemon, like getPokemonEvolutionCostFromImg, but without caching.
     *
     * @param evolutionCostImage The precut image of the evolution cost area.
     * @return the evolution cost (or -1 if absent) wrapped in Optional.of(), or Optional.absent() on scan failure
     */

    public static boolean isInColorRange(int inputColor, int matchWithColor, int allowedDistance) {
        int red = Color.red(inputColor);
        int green = Color.green(inputColor);
        int blue = Color.blue(inputColor);

        int matchRed = Color.red(matchWithColor);
        int matchGreen = Color.green(matchWithColor);
        int matchBlue = Color.blue(matchWithColor);

        boolean redInRange = red > matchRed - allowedDistance && red < matchRed + allowedDistance;
        boolean blueInRange = blue > matchBlue - allowedDistance && blue < matchBlue + allowedDistance;
        boolean greenInRange = green > matchGreen - allowedDistance && green < matchGreen + allowedDistance;

        return redInRange && blueInRange && greenInRange;
    }

    private static String fixOcrNumsToLetters(String src) {
        return src.replace("1", "l").replace("0", "o").replace("5", "s").replace("2", "z");
    }

    /**
     * Correct some OCR errors in argument where only numbers are expected.
     */
    private static String fixOcrLettersToNums(String src) {
        return src.trim().replace("S", "5").replace("s", "5").replace("O", "0").replace("B", "8")
                .replace("o", "0").replace("l", "1").replace("I", "1").replace("i", "1").replace("Z", "2")
                .replaceAll("[^0-9]+$", "");
    }

/**
     * Saves an accent color usable for the GUI overlay in the GUIColorFromPokeType class. The class is async,
     * so it doesnt update the value in GUIColorFromPokeType instantly.
     * @param pokemonImage
     */
    private void rememberGUIAccentColorBasedOnScan(Bitmap pokemonImage) {
//        int top = (int) (pokemonImage.getHeight() * 0.30);
//        int bot = (int) (pokemonImage.getHeight() * 0.35);
//        int left = (int) (pokemonImage.getWidth() * 0.45);
//        int right = (int) (pokemonImage.getWidth() * 0.55);

       int top = (int) (pokemonImage.getHeight() * 0.195);
       int bot = (int) (pokemonImage.getHeight() * 0.35);
       int left = (int) (pokemonImage.getWidth() * 0.4);
       int right = (int) (pokemonImage.getWidth() * 0.6);

        Palette.Filter filter = new Palette.Filter() {
            @Override public boolean isAllowed(int rgb, @NonNull float[] hsl) {
                if (hsl[2] > 0.7){
                    //too bright
                    return false;
                }
                if (hsl[2] < 0.2){
                    //too dark
                    return false;
                }
                if (hsl[0] > (30/360f) && hsl[0] < (60/360f)){
                    //Too yellow
                    return false;
                }

                return true;
            }
        };
        Palette.from(pokemonImage).setRegion(left, top, right, bot).addFilter(filter).generate(new Palette
                .PaletteAsyncListener() {
            public void onGenerated(Palette p) {
                int color = p.getDarkVibrantColor(0);
                if (color == 0) { //failed to find dark vibrant color, try getting a dark muted
                    color = p.getDarkMutedColor(0);
                } //Couldnt auto find color, use default primary color
                if (color == 0) {
                    color = Color.rgb(90, 90, 90);
                }

                GUIColorFromPokeType.getInstance().setColor(color);


            }
        });
    }

    /**
     * Get the range of possible levels using candy upgrade cost, if the level is potentially outside the arc-range.
     */
    private LevelRange refineLevelEstimate(int trainerLevel, Optional<Integer> pokemonPowerUpCandyCost,
                                           double estimatedPokemonLevel) {
        if (Data.MAXIMUM_WILD_POKEMON_LEVEL < Data.trainerLevelToMaxPokeLevel(trainerLevel)
                || estimatedPokemonLevel < Data.trainerLevelToMaxPokeLevel(trainerLevel)) {
            return new LevelRange(estimatedPokemonLevel); // No need for level range, arc captured level perfectly.
        }

        if (!pokemonPowerUpCandyCost.isPresent()) {
            return new LevelRange(estimatedPokemonLevel); // Couldn't read power up cost
        }

        int scannedPowerUpCost = pokemonPowerUpCandyCost.get();
        boolean matches = false;
        for (double level = 1; level < Data.MAXIMUM_POKEMON_LEVEL; level += 0.5) {
            if (Data.costForLevel(level).getAnyCandy() == scannedPowerUpCost) {
                matches = true;
                break;
            }
        }
        if (!matches) {
            return new LevelRange(estimatedPokemonLevel); // The scanned power up candy cost is invalid
        }

        // If scanned arc-level is maxed out, we need to consider that the pokemon might have an even higher level.
        double higherBound = estimatedPokemonLevel;
        for (double level = estimatedPokemonLevel + 0.5; level <= Data.MAXIMUM_WILD_POKEMON_LEVEL; level += 0.5) {
            // It either costs candy or candy xl only!
            int powerUpCostForLevel = Data.costForLevel(level).getAnyCandy();
            if (powerUpCostForLevel == scannedPowerUpCost) {
                if (higherBound < level) {
                    // Found a higher level with the same candy power up cost
                    higherBound = level;
                }
            }
        }

        // We know that the lower bound can't be lower than the actual arc-value scanned
        return new LevelRange(estimatedPokemonLevel, higherBound);
    }


    /**
     * Checks if the user has custom screen calibration, and if so, initiates the arc x,y parameters.
     *
     * @param trainerLevel the trainer level to initiate the arc points to.
     */
    private static void ensureCorrectLevelArcSettings(@NonNull GoIVSettings settings, int trainerLevel) {
        if (settings.hasManualScanCalibration()) {
            ScanPoint arcInit = new ScanPoint(ARC_INIT_POINT, settings);
            int arcRadius = Integer.valueOf(settings.getCalibrationValue(ARC_RADIUS));
            Data.setupArcPoints(arcInit, arcRadius, trainerLevel);
        }
    }

    private static int getNavigationBarHeight() {
        Pokefly pokefly = pokeflyRef.get();
        if (pokefly != null) {
            return pokefly.getCurrentNavigationBarHeight();
        } else {
            return 0;
        }
    }
}
