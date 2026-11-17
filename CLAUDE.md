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
- Todas las consultas llevan `User-Agent: MiClima/<versión> (Android)` (`data/Http`). No quitarlo:
  con el User-Agent por defecto de Android el pronóstico devolvía HTTP 403 en el teléfono, y dejó
  de fallar al agregarlo.
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
- Modelo meteorológico: `data/WeatherModel` (Automático, ECMWF, GFS, ICON, GEM, Météo-France) se
  elige en Ajustes y viaja como parámetro `models`. La caché guarda con qué modelo se bajó; si no
  coincide con el elegido, Inicio vuelve a bajar. Los modelos no automáticos pueden traer menos
  días o variables: `ForecastParser` recorta la cola vacía y lo que falta se muestra como "—".
  Los cinco ids se verificaron contra la API.
- Comparar modelos: `data/ModelComparison` pide en una sola consulta temperatura, lluvia y viento
  horarios de los cinco modelos (las claves llegan como `variable_modelo`); se guarda 3 h en
  `compare_<clave>.json`. Los cinco ids están verificados contra la API.
- Contra lo normal: `data/ClimateNormals` promedia máxima y mínima de cada fecha sobre los
  últimos 10 años (`archive-api.open-meteo.com`, una consulta de ~80 KB) con ventana de ±7 días;
  se guarda en `normals_<clave>.json` y se recalcula cada 120 días o si la ubicación se movió.
- Nieve: el pronóstico pide además `snowfall`, `snow_depth`, `freezing_level_height` y
  `snowfall_sum`. No se pudieron verificar en vivo (límite de la API): si el servicio responde
  400, `WeatherApi.fetchForecast` repite la consulta sin ellas.
- Mar: `data/Marine` (`marine-api.open-meteo.com`, olas y temperatura del agua). Opcional como el
  aire; si un lugar guardado no tiene costa (todo null) se recuerda (`Prefs.isInland`) y no se
  vuelve a consultar.
- Luna: `util/Moon` la calcula en el teléfono (fórmulas de SunCalc); verificada contra una
  efeméride con error de pocos minutos.
- Mejor momento: `util/ActivityAdvisor` puntúa cada hora 0–100 para correr, bici, tender ropa y
  asado con reglas simples, y elige el mejor tramo de hoy (o de mañana).
- Calidad del aire: `data/AirQuality` (API aparte, `air-quality-api.open-meteo.com`, AQI de
  EE. UU. y PM2.5/PM10/ozono/NO₂, ~5 días). Se pide junto con el pronóstico y es opcional: si
  falla, el pronóstico se guarda igual. Va dentro del mismo archivo de caché.
- Segundo plano: `work/RefreshWorker` (WorkManager, inicialización a demanda vía
  `MiClimaApp implements Configuration.Provider`) actualiza el lugar seleccionado cada N horas
  (`Prefs.getRefreshHours`, 0 = nunca) con `WeatherRepository.refreshSelected`, que no lee la
  ubicación: para "ubicación actual" usa las últimas coordenadas conocidas.
- Ubicación: solo `ACCESS_COARSE_LOCATION` (`util/LocationHelper`, Fused con fallback al
  LocationManager). Sin permiso se usa el último lugar guardado.

## Pantallas

- Navegación (`MainActivity`): **una página por lugar** en un `ViewPager2` (`ui/PlacePagerAdapter`,
  id de página = id del lugar o `HomeFragment.CURRENT_LOCATION` para la ubicación actual, que va
  primero). Deslizar cambia de lugar y lo marca como seleccionado (`PlaceStore.select`), que es
  lo que usan el detalle de día, la comparación, los widgets y el worker. Ajustes es un ícono en
  la barra de título (`ui/SettingsActivity`). Abajo hay una barra propia: lista de lugares,
  `ui/PageDotsView` (puntos, con un pin para la ubicación actual) y búsqueda; las dos abren
  `ui/PlacesActivity` (la búsqueda con el buscador enfocado). Al volver de Ajustes la pantalla se
  recrea; al volver de Lugares se rearman las páginas. Las vistas que se desplazan en horizontal
  dentro de una página (tira horaria, chips) van envueltas en `ui/NestedScrollableHost`.
- `ui/HomeFragment.newInstance(placeId)`, en este orden: tarjeta actual, "Lluvia · próximas 24 h"
  (`RainBarsView`, barras de probabilidad más intensas cuantos más mm, con un resumen), tira horaria
  de 16 días (`HourlyAdapter`, con fichas separadoras por día, rótulo del día visible y la línea de
  temperatura `TempLineView`: cada ficha dibuja su tramo con el rango de su día), lista de días
  (`RangeBarView` con degradé de `TempColors`; % de lluvia solo desde 20 %, en negrita desde 60 %;
  tocar un día, o una ficha de día en la tira, abre su detalle), grilla "Ahora", mejor momento,
  aire, nieve, mar, sol y luna.
- Gráficos de Inicio, todos Canvas propio: cada celda de la grilla "Ahora" y del aire lleva un
  `GaugeView` (anillo, arco con escala UV/viento, brújula que apunta hacia donde va el viento,
  medidor de presión con flecha de tendencia contra 3 h antes, barra, termómetro y vaso de lluvia;
  `CellGrid.addRow` devuelve las celdas para configurarlo). "Mejor momento" suma `ScoreStripView`
  (puntaje por hora de rojo a verde, mejor tramo enmarcado), "Nieve y cordillera" `SnowLineView`
  (perfil de montaña nevado sobre la isoterma 0 °C) y "Mar" `WaveView`.
- Íconos de clima a color propio (`@color/wx_*`, con variante nocturna; no siguen la paleta). No
  se les pone tinte salvo en el widget de bloqueo, que va en blanco.
- Sol y luna en Inicio son gráficos: `ui/SunArcView` (onda de las 24 h que cruza el horizonte en
  la salida y la puesta, tramo recorrido en amarillo y el sol en la hora actual) con salida y
  atardecer grandes, más duración del día y UV en celdas; `ui/MoonPhaseView` (disco con la parte
  iluminada según `Moon.Phase.cycle`, espejado en el hemisferio sur) con fase, % iluminado y
  salida/puesta ordenadas según cuál ocurre primero.
- `ui/DayDetailActivity`: detalle de un día sobre la caché (sin red). Resumen, tarjeta "Sol y luna"
  (`SunArcView` y `MoonPhaseView` de ese día; en otro día se resalta todo el tramo de luz), chips de variable
  (temperatura, lluvia, viento, UV, humedad, presión), gráfico `HourlyChartView` (Canvas propio:
  línea con área o barras, serie secundaria punteada, selección al tocar/arrastrar; la escala
  sale de `util/ChartScale`) y tabla hora por hora de 12 columnas con la hora fija a la izquierda.
  Flechas para pasar al día anterior/siguiente.
- `ui/ModelCompareActivity`: se abre desde el detalle de día; una línea por modelo
  (`HourlyChartView.setComparison`, con el nombre de cada modelo al final de su línea vía
  `setLineNames` para no depender solo del color), cuánto difieren y una fila por modelo con el resumen del
  día o el valor de la hora seleccionada.
- Inicio muestra bajo la tarjeta principal cuánto se aparta la máxima de hoy de lo normal, y el
  detalle de día suma las celdas "Máx./Mín. normal" (solo si los normales ya están guardados).
- Inicio suma las tarjetas "Mejor momento para…" (chips de actividad), "Nieve y cordillera",
  "Mar" (solo con costa) y "Luna"; el detalle de día suma el chip "Nieve" y la fila de luna.
- `ui/PlacesFragment` (en `PlacesActivity`): búsqueda con demora de 500 ms, resultados para
  agregar y lista de lugares con clima actual, máx/mín de hoy y hora local (de la caché), botón
  "Ver en Google Maps" (intent `geo:` a Google Maps, con respaldo a otra app o a la web) y quitar
  con deshacer. Elegir un lugar cierra la pantalla e Inicio abre su página.
- `ui/SettingsFragment`: unidades (temperatura, viento, presión, lluvia/distancia), modo, paleta,
  modelo meteorológico y frecuencia de actualización en segundo plano.
- Widgets (`widget/WeatherWidgetProvider.Kind`): FULL 4×2 (clima actual + 5 casillas cada 3 h),
  COMPACT 2×1 y LOCK 4×1 transparente con texto blanco (para pantalla de bloqueo o sobre el
  fondo). Cada variante tiene su provider (`CompactWidgetProvider`, `LockWidgetProvider`) y su
  layout; todos se dibujan desde la caché con `buildViews`. Inicio, Lugares, Ajustes y el worker
  llaman a `requestUpdate`/`updateAll` cuando cambia algo. Los que tienen tarjeta siguen el modo
  claro/oscuro del sistema, no el de la app. Declaran `home_screen|keyguard`; en Samsung
  (One UI 6/7) la pantalla de bloqueo nativa solo admite widgets del sistema, así que ahí se
  agregan con el módulo LockStar de Good Lock.
- Aviso de lluvia: `data/RainAlert` (lógica pura: primera hora con ≥60 % o ≥0,5 mm en las
  próximas 6 h, sin repetir la misma lluvia) y `work/RainNotifier` (notificación). Lo dispara
  `RefreshWorker`; está apagado por defecto y al activarlo en Ajustes pide `POST_NOTIFICATIONS`.
- Alertas configurables: `data/WeatherAlerts` (lógica pura: helada, calor, ráfagas, UV y AQI con
  umbral métrico guardado en `Prefs`; revisa las próximas 24 h) y `work/AlertNotifier`. Las
  dispara `RefreshWorker`; se avisa una vez por tipo y día (`Prefs.markAlertNotified`).
  `ui/AlertsActivity` (desde Ajustes) edita umbrales en las unidades elegidas.
- Resumen diario: `work/DailySummaryWorker` es un OneTimeWork que se reprograma solo para la hora
  elegida (por defecto 7:30); intenta actualizar y notifica máx/mín/lluvia y la diferencia con
  lo normal si está guardado.
- Inicio muestra además la tarjeta de calidad del aire (`AqiBarView`) y el detalle de día tiene el
  chip "Aire".

## Tests

`./gradlew testDebugUnitTest` — parser y formateo en JVM, más `HomeScreenTest` (Robolectric) que
abre Inicio con una caché sintética, `DayDetailTest`, `PlacesScreenTest` (lugares, páginas,
Google Maps y unidades), `CompareScreenTest`
(modelos y normales) y `AlertsTest`; guardan capturas en `app/build/screenshots/`. Datos
sintéticos en `TestData`. `MoonAndAdvisorTest` y `CompareAndNormalsTest` usan efemérides y
respuestas reales de la API como referencia.

## Publicación

`store_assets/` tiene la ficha de Google Play (`ficha_play_store.md`), el ícono 512, el gráfico
de funciones y capturas generadas desde los tests. `politica_privacidad/index.html` es la política
de privacidad (ES + EN); falta completar el correo de contacto y publicarla. Falta también
generar el `.aab` firmado.

## Pendiente

- Rehacer las capturas de `store_assets/capturas/` con la navegación y los gráficos nuevos (hoy
  muestran la barra inferior vieja con pestañas).
- Verificar en un teléfono: widgets, segundo plano, aviso de lluvia, alertas y resumen diario.
- Verificar contra la API las variables de nieve (hay respaldo si las rechaza).
