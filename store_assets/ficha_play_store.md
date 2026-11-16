# Ficha de Google Play — Mi Clima

Textos y material listos para cargar en Play Console. Los límites de caracteres son los de Play.

## Datos básicos

- **Nombre de la app** (máx. 30): `Mi Clima`
- **Categoría**: Clima
- **Paquete**: `martinlobato.com.ar.clima`
- **Política de privacidad**: publicar `politica_privacidad/index.html` y pegar su URL en Play Console.

## Descripción breve (máx. 80)

Español:

> Pronóstico hora por hora de 16 días, calidad del aire y widgets. Sin publicidad.

Inglés:

> Hour-by-hour forecast for 16 days, air quality and widgets. No ads.

## Descripción completa (máx. 4000)

Español:

> Mi Clima muestra todo el pronóstico dentro de la app, sin mandarte a ninguna página web.
>
> • Hora por hora durante 16 días: temperatura, sensación térmica, lluvia, viento, ráfagas, humedad, índice UV, presión, nubosidad y visibilidad.
> • Detalle de cada día con gráficos por variable y una tabla completa hora por hora.
> • Calidad del aire: índice AQI, PM2.5, PM10, ozono y NO₂.
> • Tus lugares: la ubicación actual y todas las ciudades que quieras guardar.
> • Widgets para la pantalla de inicio, incluido uno transparente.
> • Aviso opcional cuando viene lluvia en las próximas horas.
> • Unidades a elección (°C/°F, km/h, m/s, nudos, mph, hPa, mmHg, inHg, mm/in).
> • Elegí el modelo meteorológico: automático, ECMWF, GFS, ICON, GEM o Météo-France.
> • Tema claro, oscuro o del sistema, con cinco paletas de color.
> • Funciona sin conexión con el último pronóstico descargado.
>
> Sin publicidad, sin cuentas y sin rastreo. La app solo usa tu ubicación aproximada para pedir el pronóstico de donde estás.
>
> Datos meteorológicos: Open-Meteo.com (CC BY 4.0).

Inglés:

> Mi Clima shows the whole forecast inside the app, without sending you to a website.
>
> • Hour by hour for 16 days: temperature, feels like, rain, wind, gusts, humidity, UV index, pressure, cloud cover and visibility.
> • Day detail with a chart per variable and a full hour-by-hour table.
> • Air quality: AQI index, PM2.5, PM10, ozone and NO₂.
> • Your places: your current location and any cities you save.
> • Home screen widgets, including a transparent one.
> • Optional alert when rain is expected in the next hours.
> • Units of your choice (°C/°F, km/h, m/s, knots, mph, hPa, mmHg, inHg, mm/in).
> • Pick the weather model: automatic, ECMWF, GFS, ICON, GEM or Météo-France.
> • Light, dark or system theme with five color palettes.
> • Works offline with the last downloaded forecast.
>
> No ads, no accounts and no tracking. The app only uses your approximate location to request the forecast for where you are.
>
> Weather data: Open-Meteo.com (CC BY 4.0).

## Material gráfico (en esta carpeta)

| Archivo | Uso en Play Console |
|---|---|
| `icono_playstore_512.png` | Ícono de la app (512 × 512) |
| `grafico_funciones_1024x500.png` | Gráfico de funciones (1024 × 500) |
| `capturas/01…06-*.png` | Capturas de teléfono (1080 × 2160) |

Las capturas salen de los tests de pantalla con datos de prueba. Conviene reemplazarlas por
capturas reales del teléfono antes de publicar.

## Formulario de seguridad de los datos

- **Ubicación aproximada**: se recopila y se envía fuera del dispositivo (a Open-Meteo) solo para
  obtener el pronóstico. No se comparte con fines publicitarios ni se asocia a una identidad.
- **Términos de búsqueda** (nombre de ciudad): se envían a Open-Meteo para buscar el lugar.
- No hay cuentas, analíticas, publicidad ni identificadores de dispositivo.
- Los datos viajan cifrados (HTTPS).

## Antes de publicar

- [ ] Generar el paquete firmado (`.aab`) con una clave de subida propia.
- [ ] Completar el correo de contacto en `politica_privacidad/index.html` (dos lugares marcados
      con `[COMPLETAR CORREO DE CONTACTO]`), publicarla y cargar su URL.
- [ ] Revisar la licencia de Open-Meteo: el uso gratuito es no comercial. Una app gratuita y sin
      publicidad entra en ese uso; si se monetiza, hay que contratar su plan pago.
- [ ] Reemplazar las capturas por capturas reales.
