# Fase 2 — Curva dinámica de recuerdo

Esta fase se apoya en la Fase 1 (`agent/memoria-adaptativa-eventos`) y no modifica el scheduler FSRS ni el esquema Room.

## Semántica visual

- Línea azul continua: evolución estimada ya transcurrida.
- Línea azul discontinua: proyección futura si no se realiza otro repaso.
- Línea verde discontinua: umbral objetivo de retención del 90 %.
- Cabeza: posición y cantidad de recuerdo estimado en el momento actual.
- Círculo verde con marca: repaso completado a tiempo.
- Triángulo naranja: repaso completado tarde.
- X roja: repaso omitido.
- Círculo rojo: fallo de recuperación (`Again`).
- Círculo azul vacío: próximo repaso o repaso pendiente.

Una omisión y un fallo de recuperación son eventos diferentes. Si un repaso omitido se completa posteriormente y además falla la recuperación, la gráfica conserva los tres hechos: omisión, realización tardía y fallo cognitivo.

## Ventana temporal

El eje se adapta a horas, días, semanas, meses o años. Para evitar compresión extrema, se muestran como máximo los últimos 12 eventos de recuperación y las últimas 12 programaciones.

## Interacción y accesibilidad

Los marcadores se pueden tocar para mostrar fecha, tipo de evento y recuerdo estimado. La vista expone además una descripción textual para tecnologías de asistencia.
