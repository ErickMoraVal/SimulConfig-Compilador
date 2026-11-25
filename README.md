# Compilador Frontend — SimulConfig (v1.0)

Proyecto integrador de la materia **Lenguajes y Autómatas I (SCD-1015)**  
**Institución:** Instituto Tecnológico de Chihuahua II (TecNM)  
**Docente:** M.C. Joel Adán Saldaña Villalba  
**Autor:** Erick Jael Morales Valenzuela  

## Descripción
Herramienta frontend en Java que lee archivos fuente con extensión `.scfg` y ejecuta de manera secuencial el **Análisis Léxico** (validación de alfabeto, tokens y detección de errores léxicos) y el **Análisis Sintáctico Predictivo LL(1)** (construcción del árbol de derivación y reporte de errores gramaticales).

## Estructura del repositorio
- `src/` — Código fuente integrado (`AnalizadorSimulConfig.java`).
- `ejemplos/` — Archivos de prueba `.scfg` (caso válido, error léxico y error sintáctico).
- `docs/` — Informe técnico final con manual del lenguaje y anexos de las Unidades 1 a 5.

## Compilación y ejecución
```bash
javac src/AnalizadorSimulConfig.java
java -cp src AnalizadorSimulConfig ejemplos/ejemplo_valido.scfg
java -cp src AnalizadorSimulConfig ejemplos/ejemplo_error_lexico.scfg
java -cp src AnalizadorSimulConfig ejemplos/ejemplo_error_sintactico.scfg