# Plugin Minecraft para Enseñanza de SQL

## Introducción

Este plugin fue desarrollado como parte del Seminario de Título de Ingeniería Civil Informática en la Pontificia Universidad Católica de Valparaíso (PUCV). El proyecto tiene como objetivo construir un juego educativo en Minecraft orientado a la enseñanza de SQL de forma interactiva y lúdica.

El plugin tiene como objetivo ser una herramienta que ayude a transformar el entorno de Minecraft en un espacio de aprendizaje donde los jugadores pueden practicar y aprender consultas SQL a través de desafíos y escenarios diseñados específicamente para este propósito.

# AUTORES
# LEANDRO MAUREIRA LÓPEZ
# CAMILO GÁLVEZ CASTRO

---

## Documentación de Comandos

### Convenciones de Sintaxis

En la sintaxis de los comandos:
- Los argumentos obligatorios se indican entre signos `< >`
- Los argumentos opcionales se indican entre signos `[ ]`

---

## 1. Comandos Create

### Crear MenuZone

```
/sm create menuzone <nombre>
```

**Descripción:** Guarda una selección de WorldEdit como una menuZone.

**Requisitos previos:** Haber realizado una selección de dos posiciones con el `//wand` de WorldEdit.

**Función:** Guarda esta selección con el nombre deseado como una menuZone.

---

### Crear SQL Dungeon

```
/sm create SQLDUNGEON <mundo>
```

**Descripción:** Define un mundo como un escenario SQL DUNGEON.

**Parámetros:**
- `<mundo>`: Nombre del mundo dentro de los archivos del juego.

---

### Crear FixSlide

```
/sm create fixslide <nombre_fixslide> <nombre_zona_slide>
```

**Descripción:** Crea una presentación fija a partir de una menuZone definida como slide.

**Parámetros:**
- `<nombre_fixslide>`: Nombre de la nueva menuZone definida como fixslide.
- `<nombre_zona_slide>`: Nombre de una menuZone tipo slide ya creada.

---

### Remover MenuZone

```
/sm remove <nombre_menu_zone>
```

**Descripción:** Elimina una menuZone.

**⚠️ Advertencia:** Este comando no se puede deshacer.

---

### Crear Trigger de Fuegos Artificiales

```
/sm createfire <color> [color2] [color3]
```

**Descripción:** Crea un trigger de fuegos artificiales en la posición donde se realiza el comando.

**Características:**
- Funciona como una menuZone de una sola coordenada.
- Cuando un jugador pasa por esa coordenada, se lanzan fuegos artificiales.
- El primer color es obligatorio.
- Opcionalmente se pueden agregar hasta 3 colores en total.

---

### Crear Trigger de Fuegos Artificiales con Forma de Creeper

```
/sm createcreeperfire <color> [color2] [color3]
```

**Descripción:** Mismo comportamiento que el anterior, pero el fuego artificial tiene forma de Creeper.

---

### Listar Triggers de Fuegos Artificiales

```
/sm firework list
```

**Descripción:** Lista todos los triggers de fuegos artificiales existentes.

---

### Remover Trigger de Fuegos Artificiales

```
/sm firework remove <id>
```

**Descripción:** Permite remover un trigger de fuegos artificiales usando su ID.

**Nota:** El ID se puede obtener usando `/sm firework list`.

---

### Listar MenuZones

```
/sm list
```

**Descripción:** Lista todas las menuZones creadas.

---

## 2. Comandos Set

### Concepto de MenuZones

Las menuZones son localizaciones que detectan cuando el jugador se ubica dentro de ellas y generan un comportamiento diferenciado según cómo se configure el menutype.

### Establecer Tipo de MenuZone

```
/sm set menutype <nombre> <tipo>
```

**Descripción:** Define el tipo de menutype para una menuZone.

**Parámetros:**
- `<nombre>`: Nombre de la menuZone.
- `<tipo>`: Tipo de menuZone a definir.

**Tipos disponibles:**
- `chestport`: Para teletransportar el jugador a través de una GUI de decisión.
- `chest`: Para una GUI de opciones. *(No terminado de implementar en esta entrega)*
- `slide`: Para mostrar presentaciones gráficas.
- `laboratory`: Para definir una zona como sitio de prácticas en la base de datos.

---

## 3. Comandos de Presentaciones Gráficas (Slides)

**Requisito previo:** La menuZone debe estar configurada como tipo `slide`.

### Añadir Diapositiva

```
/sm slide <nombre_slide> add <url>
```

**Descripción:** Añade una diapositiva al slide especificado.

**Parámetros:**
- `<url>`: URL de una imagen en formato PNG. (JPG y GIF no están completamente probados)

**Comportamiento:** La diapositiva se agrega al final de la presentación y toma el número correlativo correspondiente.

---

### Editar Diapositiva

```
/sm slide <nombre_slide> edit <numero_diapositiva> <url>
```

**Descripción:** Permite editar la imagen de una diapositiva específica.

---

### Eliminar Diapositiva

```
/sm slide <nombre_slide> delete <numero_diapositiva>
```

**Descripción:** Elimina una diapositiva específica.

**Comportamiento:** Genera renumeración automática de diapositivas. Por ejemplo, si existen 3 diapositivas y se elimina la número 2, la diapositiva 3 pasará a ser la número 2.

---

### Uso de Imágenes Locales (Opcional)

**Concepto:** El plugin puede cargar imágenes desde archivos locales en lugar de descargarlas desde URLs, lo que mejora el rendimiento y permite trabajar sin conexión.

**Estructura de carpetas:**
```
plugins/SeminarioPlugin/slides_src/
├── <nombre_zona_slide>/
│   ├── 1.png
│   ├── 2.png
│   ├── 3.png
│   └── ...
```

**Funcionamiento:**
1. El plugin verifica primero si existe una imagen local en `slides_src/<nombre_zona>/<numero_diapositiva>.png`
2. Si existe, la carga directamente sin descargar desde la URL
3. Si no existe, descarga la imagen desde la URL configurada

**Ejemplo:**
- Para la zona de slides `tutorial_sql` con 5 diapositivas:
  - Crear carpeta: `plugins/SeminarioPlugin/slides_src/tutorial_sql/`
  - Colocar: `1.png`, `2.png`, `3.png`, `4.png`, `5.png`
  
**Ventajas:**
- ✅ Carga más rápida de diapositivas
- ✅ No requiere conexión a internet
- ✅ Mayor control sobre las imágenes
- ✅ Retrocompatibilidad: si no hay archivo local, descarga desde URL

**Nota:** Las imágenes deben estar en formato PNG. El plugin las redimensionará automáticamente al tamaño adecuado.

---

### FixSlides

**Concepto:** Los fixslides son un tipo extendido de slide donde la presentación se muestra permanentemente en una ubicación fija, sin necesidad de que un jugador active la zona.

**Proceso de creación:**
1. Crear una slide regular
2. Añadir las diapositivas a la slide
3. Desactivar la slide original
4. Crear el fixslide a partir de la slide desactivada (ver sección Create)

### Desactivar MenuZone

```
/sm disabled <nombre>
```

**Descripción:** Desactiva una menuzone.

---

### Fijar Posición de Renderizado

```
/sm fixslide <nombre_fixslide> posfix
```

**Descripción:** Fija la posición en la que se renderiza la presentación.

---

### Fijar Orientación

```
/sm fixslide <nombre_fixslide> fix <+X|-X|+Z|-Z>
```

**Descripción:** Define hacia qué dirección miran las diapositivas.

**Opciones de orientación:**
- `+X`: Dirección positiva del eje X
- `-X`: Dirección negativa del eje X
- `+Z`: Dirección positiva del eje Z
- `-Z`: Dirección negativa del eje Z

---

### Posicionar Botones de Navegación

```
/sm fixslide <nombre_fixslide> <backbutton|nextbutton>
```

**Descripción:** Posiciona el botón interactivo para retroceder o avanzar de diapositiva en la posición donde se realiza el comando.

**Opciones:**
- `backbutton`: Botón para retroceder
- `nextbutton`: Botón para avanzar

---

### Listar FixSlides

```
/sm fixslide list
```

**Descripción:** Muestra la lista de todos los fixslides existentes.

---

## 4. Comandos de Harry (NPC)

**Concepto:** Harry es un NPC que, cuando el jugador interactúa con él, proporciona orientación sobre ubicaciones y acciones disponibles.

### Crear Harry

```
/sm newharry <nombre>
```

**Descripción:** Crea un NPC Harry en la posición donde se realiza el comando.

---

### Añadir Línea de Diálogo

```
/sm harry <nombre> addLine <texto>
```

**Descripción:** Añade una línea de diálogo al Harry especificado.

**Formato de texto:**
- Se pueden usar códigos de color con el símbolo `&` seguido de un número o letra.
- Ejemplo: `&6Hola Mundo` aparecerá en color amarillo.
- Los códigos son similares a los códigos de sección de Minecraft (§).
- Referencia: Documentación de Bukkit para códigos de colores completos.

---

### Editar Línea de Diálogo

```
/sm harry <nombre> editLine <número_linea> <texto>
```

**Descripción:** Permite editar una línea de diálogo específica según el número de línea indicado.

---

### Ver Líneas de Diálogo

```
/sm harry <nombre> lines
```

**Descripción:** Muestra todas las líneas de diálogo de un Harry específico.

---

### Eliminar Harry

```
/sm harry <nombre> remove
```

**Descripción:** Elimina un Harry.

---

### Resetear Harry

```
/sm harry <nombre> reset
```

**Descripción:** Comando utilitario para resolver duplicaciones de entidades.

**Contexto del problema:**
- Al iniciarse el servidor, el plugin lee la información de los Harrys, limpia sus entidades y los reposiciona.
- Si un jugador no está cerca de las posiciones de los Harrys, el servidor no ha renderizado esas zonas y no puede limpiarlas.
- Usualmente se limpian solo las entidades en el spawn (renderizado por defecto), causando duplicaciones.

**Uso:** Ejecutar este comando estando cerca del Harry que presenta duplicaciones. Limpia las duplicaciones y reposiciona correctamente al Harry.

**Nota:** Idealmente debería implementarse un listener para realizar esta acción automáticamente cuando un jugador se acerca a un Harry y este se renderiza. Hasta el momento esto no se ha implementado.

---

## 5. Comandos SQL

### Ver Esquema de Base de Datos

```
/sm sql schema
```

**Descripción:** Muestra la estructura de tablas de la base de datos a través de mensajes en el chat.

**Nota:** Comando provisional agregado cuando aún no se disponía del diagrama ERD.

---

### Añadir Nivel SQL

```
/sm sql here lvl add <número> <dificultad>
```

**Descripción:** Añade un nivel SQL al mundo actual.

**Requisitos previos:** El mundo debe estar creado como SQL DUNGEON.

**Parámetros:**
- `<número>`: Número del nivel.
- `<dificultad>`: Valor de 1 a 5 que determina qué grupo de desafíos se seleccionarán del banco de datos.

**Nota:** Los comandos "here" son válidos solo para el mundo actual.

---

### Eliminar Nivel SQL

```
/sm sql here lvl delet <número>
```

**Descripción:** Elimina un nivel específico.

---

### Establecer Punto de Entrada de Nivel

```
/sm sql here set entry <número_nivel>
```

**Descripción:** Configura la posición actual como punto de interacción para un nivel.

**Requisitos previos:** El número del nivel debe haber sido añadido previamente.

**Proceso:**
1. Ejecutar el comando en la posición deseada
2. Colocar un bloque en esa posición
3. Cuando el jugador interactúe con ese bloque, podrá insertar la consulta SQL correspondiente al desafío del nivel

**Recomendación:** En el proyecto se utilizan bloques de comando por estética.

---

### Ver Información de Progreso

```
/sm sql info
```

**Descripción:** Detalla el progreso y estado dentro de los niveles.

---

### Ver Información del Banco de Desafíos

```
/sm sql bank info
```

**Descripción:** Muestra información del banco de desafíos.

**Información incluida:**
- Cantidad total de desafíos
- Desglose por nivel de dificultad
- Información adicional relevante

---

### Regenerar Desafío (DEPRECADO)

```
/sm sql bank regenerate <mundo_SQL_DUNGEON> <nivel>
```

**⚠️ Estado:** Comando deprecado.

**Contexto histórico:** Originalmente se utilizó para volver a seleccionar el desafío de un nivel específico cuando los desafíos se definían al crear el nivel. Actualmente, la selección del desafío se realiza al azar cuando un jugador entra en un nivel.

---

### Reparar Configuración SQL (DEPRECADO)

```
/sm sql repair
```

**⚠️ Estado:** Comando deprecado.

**Contexto histórico:** Asignaba desafíos a niveles no configurados.

**Advertencia:** Aunque está disponible para usar, su efecto es indeterminado.
