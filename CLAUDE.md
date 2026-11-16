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
- `data/WeatherRepository` guarda la respuesta cruda en `filesDir/forecast_cache.json`; Inicio
  muestra primero la caché y actualiza si tiene más de 30 min.
- Ubicación: solo `ACCESS_COARSE_LOCATION` (`util/LocationHelper`, Fused con fallback al
  LocationManager). Sin permiso se usa el último lugar guardado.

## Pantallas

- `ui/HomeFragment`: tarjeta actual, tira horaria de 16 días (`HourlyAdapter`, con fichas
  separadoras por día y rótulo del día visible), grilla "Ahora", lista de días con `RangeBarView`
  (tocar un día lleva la tira horaria a ese día) y tarjeta de sol.
- `ui/SettingsFragment`: modo y paleta.

## Tests

`./gradlew testDebugUnitTest` — parser y formateo en JVM, más `HomeScreenTest` (Robolectric) que
abre Inicio con una caché sintética y guarda capturas en `app/build/screenshots/`.

## Pendiente (plan por fases)

2. Detalle de día con gráficos por variable y tabla horaria completa.
3. Lugares (búsqueda con la API de geocoding de Open-Meteo, guardados) y unidades en Ajustes.
4. Calidad del aire, widget y actualización en segundo plano.
5. Aviso de lluvia próxima, ficha de Play Store.
