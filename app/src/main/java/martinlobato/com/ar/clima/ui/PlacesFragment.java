package martinlobato.com.ar.clima.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import martinlobato.com.ar.clima.Prefs;
import martinlobato.com.ar.clima.R;
import martinlobato.com.ar.clima.data.Forecast;
import martinlobato.com.ar.clima.data.GeocodingApi;
import martinlobato.com.ar.clima.data.Place;
import martinlobato.com.ar.clima.data.PlaceStore;
import martinlobato.com.ar.clima.data.WeatherCode;
import martinlobato.com.ar.clima.data.WeatherRepository;
import martinlobato.com.ar.clima.databinding.FragmentPlacesBinding;
import martinlobato.com.ar.clima.databinding.ItemPlaceBinding;
import martinlobato.com.ar.clima.util.BackgroundExecutor;
import martinlobato.com.ar.clima.util.Fmt;
import martinlobato.com.ar.clima.util.UnitFormat;
import martinlobato.com.ar.clima.widget.WeatherWidgetProvider;

/**
 * Lugares: buscar ciudades (geocoding de Open-Meteo), guardarlas y elegir cuál mostrar en Inicio.
 * La primera fila siempre es la ubicación actual del teléfono. Cada fila muestra el clima del
 * momento, la máxima y la mínima de hoy, la hora local y un acceso a Google Maps.
 */
public class PlacesFragment extends Fragment {

    private static final long SEARCH_DELAY_MS = 500;
    private static final int MIN_QUERY_LENGTH = 2;

    private FragmentPlacesBinding binding;
    private Context appCtx;
    private UnitFormat uf = UnitFormat.metric();
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Identifica la búsqueda vigente: las respuestas de búsquedas anteriores se descartan. */
    private int searchToken;
    private final Runnable searchRunnable = this::runSearch;

    private static final String ARG_SEARCH = "search";

    /** @param search true = arranca con el buscador enfocado y el teclado abierto */
    public static PlacesFragment newInstance(boolean search) {
        PlacesFragment f = new PlacesFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SEARCH, search);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlacesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        appCtx = requireContext().getApplicationContext();
        uf = Prefs.units(appCtx);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                handler.removeCallbacks(searchRunnable);
                if (s.toString().trim().length() < MIN_QUERY_LENGTH) {
                    clearResults();
                } else {
                    handler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                }
            }
        });
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(searchRunnable);
                runSearch();
                hideKeyboard();
                return true;
            }
            return false;
        });
        binding.btnClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));

        renderPlaces();

        if (savedInstanceState == null && getArguments() != null && getArguments().getBoolean(ARG_SEARCH, false)) {
            binding.etSearch.requestFocus();
            binding.etSearch.post(() -> {
                InputMethodManager imm = (InputMethodManager) appCtx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && binding != null) imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);
            });
        }
    }

    // ------------------------------------------------------------------ búsqueda

    private void clearResults() {
        searchToken++;
        binding.progressSearch.setVisibility(View.GONE);
        binding.tvSearchStatus.setVisibility(View.GONE);
        binding.listResults.setVisibility(View.GONE);
        binding.listResults.removeAllViews();
    }

    private void runSearch() {
        if (binding == null) return;
        final String query = binding.etSearch.getText().toString().trim();
        if (query.length() < MIN_QUERY_LENGTH) return;
        final int token = ++searchToken;
        final String language = Locale.getDefault().getLanguage();
        binding.progressSearch.setVisibility(View.VISIBLE);
        BackgroundExecutor.execute(() -> {
            try {
                return GeocodingApi.search(query, language);
            } catch (Exception e) {
                return null;
            }
        }, results -> {
            if (binding == null || token != searchToken) return;
            binding.progressSearch.setVisibility(View.GONE);
            showResults(query, results);
        });
    }

    /** @param results null si la búsqueda falló (sin conexión); lista vacía si no hubo coincidencias */
    private void showResults(String query, @Nullable List<Place> results) {
        binding.listResults.removeAllViews();
        if (results == null || results.isEmpty()) {
            binding.listResults.setVisibility(View.GONE);
            binding.tvSearchStatus.setVisibility(View.VISIBLE);
            binding.tvSearchStatus.setText(results == null
                    ? getString(R.string.places_search_error)
                    : getString(R.string.places_no_results, query));
            return;
        }
        binding.tvSearchStatus.setVisibility(View.GONE);
        binding.listResults.setVisibility(View.VISIBLE);
        for (final Place place : results) {
            ItemPlaceBinding row = ItemPlaceBinding.inflate(getLayoutInflater(), binding.listResults, false);
            row.tvPlaceName.setText(place.name);
            setSubtitle(row, place.subtitle());
            row.btnPlaceAction.setVisibility(View.VISIBLE);
            row.btnPlaceAction.setImageResource(R.drawable.ic_add);
            row.btnPlaceAction.setContentDescription(getString(R.string.places_add));
            View.OnClickListener add = v -> addAndOpen(place);
            row.btnPlaceAction.setOnClickListener(add);
            row.placeCard.setOnClickListener(add);
            binding.listResults.addView(row.getRoot());
        }
    }

    /** Guarda el lugar (si no estaba), lo selecciona y vuelve a Inicio para mostrar su clima. */
    private void addAndOpen(Place place) {
        PlaceStore.add(appCtx, place, -1);
        select(place);
    }

    // ------------------------------------------------------------------ guardados

    private void renderPlaces() {
        final List<Place> places = PlaceStore.list(appCtx);
        final Place selected = PlaceStore.selected(appCtx);
        binding.listPlaces.removeAllViews();
        binding.tvPlacesHint.setVisibility(places.isEmpty() ? View.VISIBLE : View.GONE);

        final Map<String, ItemPlaceBinding> rows = new HashMap<>();

        ItemPlaceBinding current = ItemPlaceBinding.inflate(getLayoutInflater(), binding.listPlaces, false);
        current.ivPlaceIcon.setVisibility(View.VISIBLE);
        current.tvPlaceName.setText(R.string.places_current_location);
        setSubtitle(current, getString(R.string.places_current_subtitle));
        markSelected(current, selected == null);
        current.placeCard.setOnClickListener(v -> select(null));
        if (Prefs.hasLastPlace(appCtx)) {
            final double lat = Prefs.getLastLat(appCtx);
            final double lon = Prefs.getLastLon(appCtx);
            final String label = Prefs.getLastPlace(appCtx);
            showMapButton(current, lat, lon, label != null ? label : getString(R.string.places_current_location));
        }
        binding.listPlaces.addView(current.getRoot());
        rows.put(WeatherRepository.KEY_CURRENT_LOCATION, current);

        for (final Place place : places) {
            ItemPlaceBinding row = ItemPlaceBinding.inflate(getLayoutInflater(), binding.listPlaces, false);
            row.tvPlaceName.setText(place.name);
            setSubtitle(row, place.subtitle());
            markSelected(row, selected != null && selected.id == place.id);
            row.placeCard.setOnClickListener(v -> select(place));
            row.btnPlaceAction.setVisibility(View.VISIBLE);
            row.btnPlaceAction.setOnClickListener(v -> delete(place));
            showMapButton(row, place.latitude, place.longitude, place.name);
            binding.listPlaces.addView(row.getRoot());
            rows.put(place.cacheKey(), row);
        }

        // Temperatura actual de cada lugar, tomada de su caché (sin red).
        final List<String> keys = new ArrayList<>(rows.keySet());
        BackgroundExecutor.execute(() -> {
            Map<String, Forecast> cached = new HashMap<>();
            for (String key : keys) {
                Forecast f = WeatherRepository.loadCached(appCtx, key);
                if (f != null && f.hourly.size() > 0) cached.put(key, f);
            }
            return cached;
        }, cached -> {
            if (binding == null) return;
            long now = System.currentTimeMillis();
            // Lugares sin caché vigente: un guion y "Sin datos todavía" en vez de un hueco.
            for (Map.Entry<String, ItemPlaceBinding> e : rows.entrySet()) {
                Forecast f = cached.get(e.getKey());
                if (f != null && now <= f.hourly.time[f.hourly.size() - 1]) continue;
                ItemPlaceBinding row = e.getValue();
                row.tvPlaceTemp.setText(Fmt.DASH);
                row.tvPlaceTemp.setVisibility(View.VISIBLE);
                row.tvPlaceHiLo.setText(R.string.place_no_data);
                row.tvPlaceHiLo.setVisibility(View.VISIBLE);
            }
            for (Map.Entry<String, Forecast> e : cached.entrySet()) {
                ItemPlaceBinding row = rows.get(e.getKey());
                Forecast f = e.getValue();
                // Solo si la caché todavía cubre este momento; un dato viejo confundiría.
                if (row == null || now > f.hourly.time[f.hourly.size() - 1]) continue;
                int i = f.hourIndexAt(now);
                if (Double.isNaN(f.hourly.temp[i])) continue;
                row.tvPlaceTemp.setText(uf.temp(f.hourly.temp[i]));
                row.tvPlaceTemp.setVisibility(View.VISIBLE);
                row.ivPlaceWeather.setImageResource(WeatherCode.icon(f.hourly.code[i], f.hourly.isDay[i]));
                row.ivPlaceWeather.setContentDescription(getString(WeatherCode.description(f.hourly.code[i])));
                row.ivPlaceWeather.setVisibility(View.VISIBLE);
                if (WeatherRepository.KEY_CURRENT_LOCATION.equals(e.getKey()) && f.place != null) {
                    setSubtitle(row, f.place);
                }
                // Máxima / mínima de hoy y hora local del lugar (como la app de Samsung).
                int d = f.dayIndexAt(now);
                if (d < f.daily.size()) {
                    row.tvPlaceHiLo.setText(getString(R.string.widget_hi_lo_short,
                            uf.temp(f.daily.tempMax[d]), uf.temp(f.daily.tempMin[d])));
                    row.tvPlaceHiLo.setVisibility(View.VISIBLE);
                }
                SimpleDateFormat local = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault());
                local.setTimeZone(f.timeZone());
                row.tvPlaceTime.setText(getString(R.string.fmt_place_time,
                        local.format(new Date(now)).replace(".", "")));
                row.tvPlaceTime.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Botón "Ver en Google Maps": abre el punto en Maps, o en otra app de mapas si no está. */
    private void showMapButton(ItemPlaceBinding row, final double lat, final double lon, final String label) {
        row.btnPlaceMap.setVisibility(View.VISIBLE);
        row.btnPlaceMap.setOnClickListener(v -> openMap(lat, lon, label));
    }

    private void openMap(double lat, double lon, String label) {
        String coords = String.format(Locale.US, "%.5f,%.5f", lat, lon);
        Uri geo = Uri.parse("geo:" + coords + "?q=" + coords + "(" + Uri.encode(label) + ")");
        Intent maps = new Intent(Intent.ACTION_VIEW, geo).setPackage("com.google.android.apps.maps");
        try {
            startActivity(maps);
        } catch (ActivityNotFoundException noGoogleMaps) {
            try {
                // Sin Google Maps: cualquier app de mapas, o el navegador con Google Maps web.
                startActivity(new Intent(Intent.ACTION_VIEW, geo));
            } catch (ActivityNotFoundException noMaps) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + coords)));
                } catch (ActivityNotFoundException nothing) {
                    Toast.makeText(appCtx, R.string.places_no_maps, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static void setSubtitle(ItemPlaceBinding row, String subtitle) {
        row.tvPlaceSubtitle.setText(subtitle);
        row.tvPlaceSubtitle.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void markSelected(ItemPlaceBinding row, boolean selected) {
        if (!selected) return;
        float density = getResources().getDisplayMetrics().density;
        row.placeCard.setStrokeColor(MaterialColors.getColor(row.getRoot(), androidx.appcompat.R.attr.colorPrimary));
        row.placeCard.setStrokeWidth(Math.round(2 * density));
        row.placeCard.setSelected(true);
    }

    /** Elige el lugar y vuelve a Inicio, que se abre en la página de ese lugar. */
    private void select(@Nullable Place place) {
        PlaceStore.select(appCtx, place);
        WeatherWidgetProvider.requestUpdate(appCtx);
        hideKeyboard();
        requireActivity().finish();
    }

    private void delete(final Place place) {
        final List<Place> before = PlaceStore.list(appCtx);
        int index = -1;
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i).id == place.id) index = i;
        }
        final int position = index;
        final Place selected = PlaceStore.selected(appCtx);
        final boolean wasSelected = selected != null && selected.id == place.id;

        PlaceStore.remove(appCtx, place.id);
        renderPlaces();
        Snackbar.make(binding.getRoot(), getString(R.string.places_deleted, place.name), Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> {
                    PlaceStore.add(appCtx, place, position);
                    if (wasSelected) PlaceStore.select(appCtx, place);
                    if (binding != null) renderPlaces();
                })
                .show();
    }

    private void hideKeyboard() {
        if (binding == null) return;
        InputMethodManager imm = (InputMethodManager) appCtx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
        }
        binding.etSearch.clearFocus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(searchRunnable);
        searchToken++;
        binding = null;
    }
}
