# Fase 4 — Personalización y panel operativo

## Objetivo

Permitir que el usuario ajuste el nivel de recuerdo que desea conservar y ofrecer una visión operativa de la carga real de estudio sin inventar predicciones que no existen en los datos.

## Objetivo de recuerdo

La aplicación ofrece tres perfiles globales:

- 85 %: menos frecuencia y menor carga de repasos.
- 90 %: equilibrio predeterminado.
- 95 %: mayor frecuencia para sostener una probabilidad de recuerdo más alta.

El valor se guarda en `SharedPreferences` y se utiliza al construir `FsrsMemoryEngine`.

### Regla de transición

Cambiar el perfil:

- actualiza inmediatamente el umbral mostrado en las curvas;
- modifica los intervalos que FSRS calcule a partir del próximo repaso;
- no reescribe `nextReviewAt` ni las programaciones ya persistidas;
- no altera dificultad, estabilidad, lapsos ni historial.

Esto evita que una preferencia nueva modifique retrospectivamente decisiones ya registradas.

## Panel operativo

El panel resume:

- repasos pendientes ahora;
- repasos omitidos;
- repasos ya programados para los próximos siete días;
- recuerdo medio entre los temas que ya tienen al menos un repaso;
- temas revisados por debajo del objetivo seleccionado;
- puntualidad de los repasos completados;
- número de repasos registrados durante los últimos siete días.

Los temas nuevos sin un repaso cognitivo se excluyen del recuerdo medio, porque todavía no existe una estimación útil de recuperación.

## Carga de 14 días

El gráfico cuenta únicamente el próximo repaso actualmente persistido de cada tema.

- La barra de `Hoy` incluye temas vencidos y temas programados para el día actual.
- Las demás barras muestran los próximos trece días según la zona horaria local.
- No se simulan cadenas futuras de repasos porque sus fechas dependen de respuestas que todavía no han ocurrido.

Por tanto, el gráfico representa **carga ya programada**, no una predicción completa del futuro.

## Cola de prioridades

Se muestran como máximo cinco temas y se ordenan por categorías explícitas:

1. repaso omitido;
2. repaso vencido;
3. recuerdo estimado por debajo del objetivo;
4. próximo repaso dentro de siete días.

Dentro de cada categoría se utiliza la fecha programada; para los temas bajo el objetivo se prioriza primero la menor recuperabilidad.

No existe una puntuación compuesta o un índice de riesgo oculto.

## Métricas de cumplimiento

La puntualidad se calcula como:

`completados a tiempo / (completados a tiempo + completados tarde)`

Los registros migrados y las omisiones sin repaso completado no se incluyen en ese denominador.

## Pruebas

La fase añade pruebas para:

- normalización de 85 %, 90 % y 95 %;
- uso del objetivo seleccionado por el panel;
- conteo de pendientes, omitidos y próximos;
- exclusión de temas nuevos del recuerdo medio;
- prioridad de las omisiones;
- cálculo de puntualidad;
- carga diaria según zona horaria;
- intervalo no mayor con un objetivo de retención más alto;
- conservación de una fecha persistida al cambiar de motor.
