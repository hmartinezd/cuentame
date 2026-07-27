# Guía del Usuario de Cuentame

Bienvenido a **Cuentame**, su gestor de inventario para restaurantes con almacenamiento local. Esta guía explica cómo usar la aplicación para mantener niveles de stock precisos y monitorear tendencias de valor de inventario, compras y mermas.

## 1. Configuración Inicial

Al abrir Cuentame por primera vez, se le guiará por el proceso de **Onboarding**:
*   **Nombre del Restaurante:** Ingrese el nombre de su establecimiento.
*   **Moneda e Idioma:** Elija su moneda principal (ej. USD) y su idioma de preferencia.

## 2. Gestión de la Base del Inventario

Antes de registrar actividad, debe definir sus ubicaciones de almacenamiento y sus artículos.

### Áreas de Inventario
Defina dónde guarda su mercancía (ej. "Cámara Frigorífica", "Bodega", "Bar").
1.  Vaya a **Configuración** -> **Áreas de Inventario**.
2.  Añada una nueva área con un nombre descriptivo.
3.  Puede archivar las áreas que ya no utilice.

### Ingredientes
Cree una lista de todos los artículos que desea rastrear.
1.  Vaya a la pestaña **Inventario**.
2.  Toque el botón **Añadir**.
3.  Ingrese el nombre y elija una unidad base (ej. masa para lbs/kg, volumen para litros/galones).

### Opciones de Unidad
Las opciones de unidad son fundamentales. Definen cómo mide el artículo en el mundo real.
*   **Unidad Base:** Cómo se cuenta el artículo en su inventario final (ej. Lb).
*   **Unidad de Compra:** Cómo lo compra (ej. Caja de 50 Lbs).
*   **Unidad de Conteo:** Cómo lo cuenta durante un inventario físico (ej. Bolsa de 5 Lbs).
*   *Requisito:* Un ingrediente debe tener al menos una opción de unidad antes de poder usarse en registros.

## 3. Operaciones Diarias

### Compras
Registre la nueva mercancía que llega de sus proveedores.
1.  Toque **Nueva Compra** en el Panel Principal.
2.  Seleccione un **Proveedor** e ingrese el **Número de Factura** y la **Fecha**.
3.  Añada líneas para cada ingrediente, especificando la cantidad y el costo.
4.  **Importante:** Una compra es un **BORRADOR** (DRAFT) hasta que toca **Publicar** (Post). Solo las compras PUBLICADAS actualizan sus niveles de stock y aparecen en los reportes.

### Mermas (Waste)
Registre los artículos que se pierden, se echan a perder o se desechan.
1.  Toque **Registrar Merma** en el Panel Principal.
2.  Seleccione el ingrediente, el área donde estaba, la cantidad y el **Motivo** (ej. Descompuesto, Expirado).
3.  Revise el **valor estimado** de la pérdida basado en costos históricos.
4.  **Publique** el evento de merma para finalizar el registro.

### Conteos de Stock
Realice un inventario físico para verificar su stock real.
1.  Toque **Iniciar Conteo** en el Panel Principal.
2.  Seleccione las **Áreas** que está contando.
3.  Ingrese las cantidades físicas encontradas para cada artículo.
4.  Una vez **Completado**, la aplicación actualizará sus saldos de inventario para que coincidan con lo que contó físicamente.

## 4. Panel y Reportes

### Panel Principal (Dashboard)
*   **Valor del Inventario:** El valor monetario total de su stock actual.
*   **Alertas Operativas:** Avisos inmediatos sobre saldos negativos o falta de costos en ingredientes.
*   **Actividad Reciente:** Una vista rápida de sus últimos documentos publicados.

### Resumen de Reportes
Seleccione entre periodos de **7, 30 o 90 días**.
*   **Gasto en Compras:** Compare cuánto gastó en mercancía nueva contra el periodo anterior.
*   **Valor de Mermas:** Revise sus pérdidas totales a lo largo del tiempo.
*   **Integridad de Datos:** Vea cuántos de sus artículos tienen costos asignados.
*   **Principales Mermas:** Identifique los 5 artículos que causan sus mayores pérdidas financieras.

### Reportes Detallados
Puede profundizar en cada sección del Resumen de Reportes para ver los registros exactos:
*   **Detalle de Inventario:** Muestra cada ingrediente actualmente en stock, su cantidad total, costo promedio y valor calculado. Resalta los artículos con **costos faltantes** o **saldos negativos** en áreas específicas.
*   **Detalles de Compra:** Enumera todos los recibos **PUBLICADOS** en el periodo seleccionado. Puede ver el proveedor, la fecha, la cantidad de líneas y el total de cada compra.
*   **Detalles de Merma:** Enumera todos los eventos de Merma **PUBLICADOS**. Los valores mostrados utilizan el **costo histórico** capturado en el momento en que se registró la merma, lo que garantiza que los informes sigan siendo precisos incluso si los costos actuales cambian.

**Nota sobre la selección de rango:** Cuando abre un reporte detallado de Compras o Merma, este hereda el rango de fechas seleccionado actualmente en el Resumen de Reportes. Cambiar el rango dentro de la pantalla de detalles actualizará ese reporte inmediatamente, pero no cambiará los ajustes de su Resumen general.

## 5. Estados de los Documentos

*   **BORRADOR (DRAFT):** Aún se está editando. No afecta al inventario.
*   **PUBLICADO (POSTED):** Finalizado y activo.
*   **ANULADO (VOIDED):** Cancelado después de ser publicado. Excluido de los totales activos.

## 6. Datos Locales y Seguridad

*   **Privacidad:** Todos sus datos comerciales se guardan únicamente en este dispositivo.
*   **Internet:** No se requiere conexión a internet para operar.
*   **Respaldos:** Actualmente no hay respaldo en la nube. Si pierde su dispositivo o borra la aplicación, sus datos se perderán. Recomendamos conservar copias físicas de facturas críticas.

## 7. Solución de Problemas

*   **Costos Faltantes:** Si un ingrediente muestra un valor de "$0.00", asegúrese de haber publicado al menos una compra con un costo válido para ese artículo.
*   **Saldos Negativos:** Esto sucede si registra más mermas o ventas de las que ha registrado en compras. Realice un **Conteo de Stock** para restablecer el nivel correcto.
