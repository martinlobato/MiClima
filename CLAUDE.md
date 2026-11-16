# CLAUDE.md — Contexto técnico de Mi Clima

App Android de clima con **toda la información dentro de la app**: pronóstico hora por hora de los
16 días (no solo 24 h), sin enlaces a la web del proveedor.

- `applicationId` / `namespace`: `martinlobato.com.ar.clima` · nombre visible: **Mi Clima**
- Stack idéntico a MyLoc/Ubica: **Java + XML + ViewBinding**, una Activity host + Fragments,
  Material3, minSdk 24. Sin Kotlin, sin Compose, sin librerías de red ni de gráficos.
- Tema: copiado de MyLoc (5 paletas × claro/oscuro/sistema, tokens tonales en `colors.xml`,
  `ThemeUtils`, `Prefs`). Estilo visual del handoff `design-v2` de MyLoc: tarjetas radio 24/20,
  celdas "LABEL / valor" radio 14, íconos de trazo.

## Datos

- **Open-Meteo** (`api.open-meteo.com/v1/forecast`), sin API key. Uso gratuito = no comercial y
  con atribución (se muestra en Inicio y Ajustes). Si la app se monetiza hay que pasar al plan pago.
- `data/WeatherApi` arma la URL (lat/lon redondeadas a 2 decimales) y baja el JSON con
  `HttpURLConnection`. `data/ForecastParser` lo convierte en `Forecast` (null → `NaN`; los tiempos
  ISO son hora local **del lugar**, se convierten a epoch con la zona que informa la API).
- `data/WeatherRepository` guarda la respuesta cruda en disco, **una caché por lugar**
  (`forecast_cache.json` para la ubicación actual, `forecast_p<id>.json` para cada lugar guardado);
  Inicio muestra primero la caché y actualiza si tiene más de 30 min.
- Lugares: `data/GeocodingApi` busca ciudades en `geocoding-api.open-meteo.com`; `data/PlaceStore`
  guarda los lugares y el seleccionado en SharedPreferences (JSON). Seleccionado null = ubicación
  actual del teléfono.
- Unidades: la API siempre se consulta en métrico; `util/Units` convierte y `util/UnitFormat`
  formatea según lo elegido en Ajustes (`Prefs.units`). Nunca formatear valores con unidad a mano:
  pasar siempre por `UnitFormat`. La unidad de lluvia define también la de distancia (mm→km, in→mi).
- Ubicación: solo `ACCESS_COARSE_LOCATION` (`util/LocationHelper`, Fused con fallback al
  LocationManager). Sin permiso se usa el último lugar guardado.

## Pantallas

- `ui/HomeFragment`: tarjeta actual, tira horaria de 16 días (`HourlyAdapter`, con fichas
  separadoras por día y rótulo del día visible), grilla "Ahora", lista de días con `RangeBarView`
  (tocar un día, o una ficha de día en la tira, abre su detalle) y tarjeta de sol.
- `ui/DayDetailActivity`: detalle de un día sobre la caché (sin red). Resumen, chips de variable
  (temperatura, lluvia, viento, UV, humedad, presión), gráfico `HourlyChartView` (Canvas propio:
  línea con área o barras, serie secundaria punteada, selección al tocar/arrastrar; la escala
  sale de `util/ChartScale`) y tabla hora por hora de 12 columnas con la hora fija a la izquierda.
  Flechas para pasar al día anterior/siguiente.
- `ui/PlacesFragment` (pestaña Lugares): búsqueda con demora de 500 ms, resultados para agregar,
  lista de guardados con la temperatura tomada de la caché, selección (vuelve a Inicio) y quitar
  con deshacer.
- `ui/SettingsFragment`: unidades (temperatura, viento, presión, lluvia/distancia), modo y paleta.

## Tests

`./gradlew testDebugUnitTest` — parser y formateo en JVM, más `HomeScreenTest` (Robolectric) que
abre Inicio con una caché sintética, `DayDetailTest` (detalle de día) y `PlacesScreenTest`
(lugares y unidades); guardan capturas en `app/build/screenshots/`. Datos sintéticos en `TestData`.

## Pendiente (plan por fases)

4. Calidad del aire, widget y actualización en segundo plano.
5. Aviso de lluvia próxima, ficha de Play Store.

Pendiente de decidir: selector de modelo meteorológico en Ajustes (parámetro `models` de
Open-Meteo: ECMWF, GFS, ICON…) como forma de hacer configurable el origen de los datos.
