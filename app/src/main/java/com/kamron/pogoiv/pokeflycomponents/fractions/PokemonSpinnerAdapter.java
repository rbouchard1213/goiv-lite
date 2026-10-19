package com.kamron.pogoiv.pokeflycomponents.fractions;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.kamron.pogoiv.R;
import com.kamron.pogoiv.scanlogic.Pokemon;

import java.util.List;

/**
 * Minimal spinner adapter for goiv-lite (replaces the cut widgets.PokemonSpinnerAdapter).
 * Shows pokedex number, name and form in the spinner rows.
 */
public class PokemonSpinnerAdapter extends ArrayAdapter<Pokemon> {

    private final LayoutInflater inflater;

    public PokemonSpinnerAdapter(@NonNull Context context, int resource, @NonNull List<Pokemon> objects) {
        super(context, resource, objects);
        inflater = LayoutInflater.from(context);
    }

    public void updatePokemonList(@NonNull List<Pokemon> pokemons) {
        clear();
        addAll(pokemons);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return bindRow(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return bindRow(position, convertView, parent);
    }

    private View bindRow(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.spinner_pokemon, parent, false);
        }
        Pokemon pokemon = getItem(position);
        if (pokemon != null) {
            ((TextView) row.findViewById(R.id.spinnerPokedexNum)).setText("#" + (pokemon.number + 1));
            ((TextView) row.findViewById(R.id.spinnerPokeName)).setText(pokemon.name);
            ((TextView) row.findViewById(R.id.spinnerForm)).setText(pokemon.formName);
        }
        return row;
    }
}
