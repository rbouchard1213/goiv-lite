package com.kamron.pogoiv.pokeflycomponents.fractions;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.View;

import com.kamron.pogoiv.Pokefly;
import com.kamron.pogoiv.R;
import com.kamron.pogoiv.databinding.FractionIvResultBinding;
import com.kamron.pogoiv.utils.GUIColorFromPokeType;
import com.kamron.pogoiv.utils.GuiUtil;
import com.kamron.pogoiv.utils.ReactiveColorListener;
import com.kamron.pogoiv.utils.fractions.Fraction;


public class IVResultFraction extends Fraction implements ReactiveColorListener {

    private FractionIvResultBinding binding;
    private Context context;
    private Pokefly pokefly;


    public IVResultFraction(@NonNull Pokefly pokefly) {
        this.context = pokefly;
        this.pokefly = pokefly;
    }


    @Override
    public int getLayoutResId() {
        return R.layout.fraction_iv_result;
    }

    @Override public void onCreate(@NonNull View rootView) {
        binding = FractionIvResultBinding.bind(rootView);

        // goiv-lite v1: hide navigation to cut fractions (combinations / power-up / moveset / share)
        binding.tvSeeAllPossibilities.setVisibility(View.GONE);
        binding.powerUpButton.setVisibility(View.GONE);
        binding.movesetButton.setVisibility(View.GONE);
        binding.shareWithOtherApp.setVisibility(View.GONE);

        binding.btnBack.setOnClickListener(v -> pokefly.navigateToPreferredStartFraction());
        binding.btnClose.setOnClickListener(v -> pokefly.closeInfoDialog());

        // Show IV information
        Pokefly.scanResult.sortIVCombinations();
        populateResultsHeader();

        if (Pokefly.scanResult.getIVCombinationsCount() == 0) {
            populateNotIVMatch();
        } else if (Pokefly.scanResult.getIVCombinationsCount() == 1) {
            populateSingleIVMatch();
        } else { // More than a match
            populateMultipleIVMatch();
        }
        setResultScreenPercentageRange(); //color codes the result
        GUIColorFromPokeType.getInstance().setListenTo(this);
        updateGuiColors();
        setBasePokemonStatsText();
    }

    private void setBasePokemonStatsText() {
        int att = Pokefly.scanResult.pokemon.baseAttack;
        int def = Pokefly.scanResult.pokemon.baseDefense;
        int sta = Pokefly.scanResult.pokemon.baseStamina;
        binding.baseStatsResults.setText("Base stats: Att - " + att + " Def - " + def + " Sta - " + sta);
    }


    @Override public void onDestroy() {
        // Nothing to do
        GUIColorFromPokeType.getInstance().removeListener(this);
    }

    @Override
    public Anchor getAnchor() {
        return Anchor.BOTTOM;
    }

    @Override
    public int getVerticalOffset(@NonNull DisplayMetrics displayMetrics) {
        return 0;
    }

    /**
     * Shows the name and level of the pokemon in the results dialog.
     */
    private void populateResultsHeader() {
        binding.resultsPokemonName.setText(Pokefly.scanResult.pokemon.toString());
        binding.resultsPokemonLevel.setText(
                context.getString(R.string.level_num, Pokefly.scanResult.levelRange.toString()));
    }

    /**
     * Populates the result screen with error warning.
     */
    private void populateNotIVMatch() {
        binding.llMaxIV.setVisibility(View.VISIBLE);
        binding.llMinIV.setVisibility(View.VISIBLE);
        binding.llSingleMatch.setVisibility(View.GONE);
        binding.llMultipleIVMatches.setVisibility(View.VISIBLE);
        binding.tvAvgIV.setText(context.getString(R.string.avg));

        binding.resultsCombinations.setText(
                context.getString(R.string.possible_iv_combinations, Pokefly.scanResult.getIVCombinationsCount()));

        binding.correctCPLevel.setVisibility(View.VISIBLE);
    }


    /**
     * Populates the result screen with the layout as if it's a single result.
     */
    private void populateSingleIVMatch() {
        binding.llMaxIV.setVisibility(View.GONE);
        binding.llMinIV.setVisibility(View.GONE);
        binding.tvAvgIV.setText(context.getString(R.string.iv));
        binding.resultsAttack.setText(String.valueOf(Pokefly.scanResult.getIVCombinationAt(0).att));
        binding.resultsDefense.setText(String.valueOf(Pokefly.scanResult.getIVCombinationAt(0).def));
        binding.resultsHP.setText(String.valueOf(Pokefly.scanResult.getIVCombinationAt(0).sta));

        GuiUtil.setTextColorByIV(binding.resultsAttack, Pokefly.scanResult.getIVCombinationAt(0).att);
        GuiUtil.setTextColorByIV(binding.resultsDefense, Pokefly.scanResult.getIVCombinationAt(0).def);
        GuiUtil.setTextColorByIV(binding.resultsHP, Pokefly.scanResult.getIVCombinationAt(0).sta);

        binding.llSingleMatch.setVisibility(View.VISIBLE);
        int possibleCombinationsCount = Pokefly.scanResult.getIVCombinations().size();
        if (possibleCombinationsCount > 1) {
            binding.llMultipleIVMatches.setVisibility(View.VISIBLE);
            binding.resultsCombinations.setText(
                    context.getString(R.string.possible_iv_combinations, possibleCombinationsCount));
        } else {
            binding.llMultipleIVMatches.setVisibility(View.GONE);
        }
        binding.correctCPLevel.setVisibility(View.GONE);
    }

    /**
     * Populates the result screen with the layout as if its multiple results.
     */
    private void populateMultipleIVMatch() {
        binding.llMaxIV.setVisibility(View.VISIBLE);
        binding.llMinIV.setVisibility(View.VISIBLE);
        binding.llSingleMatch.setVisibility(View.GONE);
        binding.llMultipleIVMatches.setVisibility(View.VISIBLE);
        binding.tvAvgIV.setText(context.getString(R.string.avg));

        binding.resultsCombinations.setText(
                context.getString(R.string.possible_iv_combinations, Pokefly.scanResult.getIVCombinationsCount()));

        binding.correctCPLevel.setVisibility(View.GONE);
    }

    /**
     * Fixes the three boxes that show iv range color and text.
     */
    private void setResultScreenPercentageRange() {
        int low = 0;
        int ave = 0;
        int high = 0;
        if (Pokefly.scanResult.getIVCombinationsCount() > 0) {
            low = Pokefly.scanResult.getLowestIVCombination().percentPerfect;
            ave = Pokefly.scanResult.getIVPercentAvg();
            high = Pokefly.scanResult.getHighestIVCombination().percentPerfect;
        }
        GuiUtil.setTextColorByPercentage(binding.resultsMinPercentage, low);
        GuiUtil.setTextColorByPercentage(binding.resultsAvePercentage, ave);
        GuiUtil.setTextColorByPercentage(binding.resultsMaxPercentage, high);


        if (Pokefly.scanResult.getIVCombinationsCount() > 0) {
            binding.resultsMinPercentage.setText(context.getString(R.string.percent, low));
            binding.resultsAvePercentage.setText(context.getString(R.string.percent, ave));
            binding.resultsMaxPercentage.setText(context.getString(R.string.percent, high));
        } else {
            String unknown_percent = context.getString(R.string.unknown_percent);
            binding.resultsMinPercentage.setText(unknown_percent);
            binding.resultsAvePercentage.setText(unknown_percent);
            binding.resultsMaxPercentage.setText(unknown_percent);
        }
    }

    @Override public void updateGuiColors() {
        int c = GUIColorFromPokeType.getInstance().getColor();
        binding.ivResultsHeader.setBackgroundColor(c);
    }
}
