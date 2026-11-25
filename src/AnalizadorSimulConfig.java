import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalizadorSimulConfig {

    enum TipoToken {
        INICIO, FIN, RESERVADA, IDENTIFICADOR, NUMERO, CADENA,
        IGUAL, PyC, MAS, MENOS, POR, DIV, PAR_IZQ, PAR_DER, EOF
    }

    static class Token {
        final TipoToken tipo;
        final String lexema;
        final int linea;

        Token(TipoToken tipo, String lexema, int linea) {
            this.tipo = tipo;
            this.lexema = lexema;
            this.linea = linea;
        }
    }

    static class ResultadoLexico {
        final List<Token> tokens;
        final boolean exitoso;

        ResultadoLexico(List<Token> tokens, boolean exitoso) {
            this.tokens = tokens;
            this.exitoso = exitoso;
        }
    }

    static class ErrorSintacticoException extends RuntimeException {
        ErrorSintacticoException(String msg) {
            super(msg);
        }
    }

    // --- FASE 1: ANALIZADOR LEXICO ---
    private static final String REGEX_LEXICO =
            "(?<INICIO>\\bInicio\\b)" +
            "|(?<FIN>\\bFin\\b)" +
            "|(?<RESERVADA>\\b(Modelo|Servidores|TiempoLlegada|TiempoServicio|FilaMaxima|Distribucion|Capacidad|Unidad)\\b)" +
            "|(?<IDENTIFICADOR>[a-zA-Z][a-zA-Z0-9_]*)" +
            "|(?<NUMERO>[0-9]+)" +
            "|(?<CADENA>\"[^\"]*\")" +
            "|(?<IGUAL>=)" +
            "|(?<PyC>;)" +
            "|(?<MAS>\\+)" +
            "|(?<MENOS>-)" +
            "|(?<POR>\\*)" +
            "|(?<DIV>/)" +
            "|(?<PARIZQ>\\()" +
            "|(?<PARDER>\\))" +
            "|(?<ESPACIO>[ \\t\\r]+)" +
            "|(?<ERRORCADENA>\"[^\"]*$)" +
            "|(?<ERROR>.)";

    private static final Pattern PATRON_LEXICO = Pattern.compile(REGEX_LEXICO);

    public static ResultadoLexico ejecutarFaseLexica(String codigoFuente) {
        List<Token> tokens = new ArrayList<>();
        boolean sinErrores = true;
        String[] lineas = codigoFuente.split("\\R", -1);

        System.out.println("=== FASE 1: ANALISIS LEXICO ===");
        System.out.printf("%-20s | %-22s | %-5s\n", "TIPO TOKEN", "LEXEMA", "LINEA");
        System.out.println("--------------------------------------------------------");

        for (int i = 0; i < lineas.length; i++) {
            int numLinea = i + 1;
            Matcher m = PATRON_LEXICO.matcher(lineas[i]);

            while (m.find()) {
                if (m.group("ESPACIO") != null) {
                    continue;
                } else if (m.group("INICIO") != null) {
                    agregarYMostrar(tokens, TipoToken.INICIO, m.group("INICIO"), numLinea);
                } else if (m.group("FIN") != null) {
                    agregarYMostrar(tokens, TipoToken.FIN, m.group("FIN"), numLinea);
                } else if (m.group("RESERVADA") != null) {
                    agregarYMostrar(tokens, TipoToken.RESERVADA, m.group("RESERVADA"), numLinea);
                } else if (m.group("IDENTIFICADOR") != null) {
                    agregarYMostrar(tokens, TipoToken.IDENTIFICADOR, m.group("IDENTIFICADOR"), numLinea);
                } else if (m.group("NUMERO") != null) {
                    agregarYMostrar(tokens, TipoToken.NUMERO, m.group("NUMERO"), numLinea);
                } else if (m.group("CADENA") != null) {
                    agregarYMostrar(tokens, TipoToken.CADENA, m.group("CADENA"), numLinea);
                } else if (m.group("IGUAL") != null) {
                    agregarYMostrar(tokens, TipoToken.IGUAL, m.group("IGUAL"), numLinea);
                } else if (m.group("PyC") != null) {
                    agregarYMostrar(tokens, TipoToken.PyC, m.group("PyC"), numLinea);
                } else if (m.group("MAS") != null) {
                    agregarYMostrar(tokens, TipoToken.MAS, m.group("MAS"), numLinea);
                } else if (m.group("MENOS") != null) {
                    agregarYMostrar(tokens, TipoToken.MENOS, m.group("MENOS"), numLinea);
                } else if (m.group("POR") != null) {
                    agregarYMostrar(tokens, TipoToken.POR, m.group("POR"), numLinea);
                } else if (m.group("DIV") != null) {
                    agregarYMostrar(tokens, TipoToken.DIV, m.group("DIV"), numLinea);
                } else if (m.group("PARIZQ") != null) {
                    agregarYMostrar(tokens, TipoToken.PAR_IZQ, m.group("PARIZQ"), numLinea);
                } else if (m.group("PARDER") != null) {
                    agregarYMostrar(tokens, TipoToken.PAR_DER, m.group("PARDER"), numLinea);
                } else if (m.group("ERRORCADENA") != null) {
                    System.out.printf("[ERROR LEXICO] Linea %d: Cadena sin cerrar -> %s\n", numLinea, m.group("ERRORCADENA"));
                    sinErrores = false;
                } else if (m.group("ERROR") != null) {
                    System.out.printf("[ERROR LEXICO] Linea %d: Simbolo no reconocido -> '%s'\n", numLinea, m.group("ERROR"));
                    sinErrores = false;
                }
            }
        }

        tokens.add(new Token(TipoToken.EOF, "EOF", lineas.length));
        System.out.println("--------------------------------------------------------");
        return new ResultadoLexico(tokens, sinErrores);
    }

    private static void agregarYMostrar(List<Token> tokens, TipoToken tipo, String lexema, int linea) {
        tokens.add(new Token(tipo, lexema, linea));
        System.out.printf("%-20s | %-22s | %-5d\n", tipo, lexema, linea);
    }

    // --- FASE 2: ANALIZADOR SINTACTICO LL(1) ---
    private final List<Token> tokens;
    private int pos = 0;
    private final StringBuilder arbol = new StringBuilder();

    public AnalizadorSimulConfig(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Token actual() {
        return tokens.get(pos);
    }

    private void nodo(String indent, String etiqueta) {
        arbol.append(indent).append("|-- ").append(etiqueta).append("\n");
    }

    private void consumir(TipoToken esperado, String indent) {
        Token t = actual();
        if (t.tipo == esperado) {
            nodo(indent, "[Token] " + t.tipo + " ('" + t.lexema + "')");
            pos++;
        } else {
            throw new ErrorSintacticoException(
                "Linea " + t.linea + ": Se esperaba " + esperado +
                ", pero se encontro " + t.tipo + " ('" + t.lexema + "')."
            );
        }
    }

    public String analizarSintaxis() {
        arbol.setLength(0);
        pos = 0;
        parsePrograma("");
        if (actual().tipo != TipoToken.EOF) {
            Token t = actual();
            throw new ErrorSintacticoException(
                "Linea " + t.linea + ": Se esperaba EOF despues de 'Fin', pero se encontro " +
                t.tipo + " ('" + t.lexema + "')."
            );
        }
        return arbol.toString();
    }

    private void parsePrograma(String indent) {
        nodo(indent, "<programa>");
        String child = indent + "|   ";
        consumir(TipoToken.INICIO, child);
        parseListaSentencias(child);
        consumir(TipoToken.FIN, child);
    }

    private void parseListaSentencias(String indent) {
        nodo(indent, "<lista_sentencias>");
        String child = indent + "|   ";
        if (actual().tipo == TipoToken.RESERVADA || actual().tipo == TipoToken.IDENTIFICADOR) {
            parseSentencia(child);
            parseListaSentencias(child);
        } else {
            nodo(child, "epsilon (vacio)");
        }
    }

    private void parseSentencia(String indent) {
        nodo(indent, "<sentencia>");
        String child = indent + "|   ";
        if (actual().tipo == TipoToken.RESERVADA) {
            consumir(TipoToken.RESERVADA, child);
        } else if (actual().tipo == TipoToken.IDENTIFICADOR) {
            consumir(TipoToken.IDENTIFICADOR, child);
        } else {
            Token t = actual();
            throw new ErrorSintacticoException(
                "Linea " + t.linea + ": Se esperaba RESERVADA o IDENTIFICADOR, pero se encontro " +
                t.tipo + " ('" + t.lexema + "')."
            );
        }
        consumir(TipoToken.IGUAL, child);
        parseExpresion(child);
        consumir(TipoToken.PyC, child);
    }

    private void parseExpresion(String indent) {
        nodo(indent, "<expresion>");
        String child = indent + "|   ";
        parseTermino(child);
        parseExprPrima(child);
    }

    private void parseExprPrima(String indent) {
        nodo(indent, "<expr_prima>");
        String child = indent + "|   ";
        if (actual().tipo == TipoToken.MAS || actual().tipo == TipoToken.MENOS) {
            consumir(actual().tipo, child);
            parseTermino(child);
            parseExprPrima(child);
        } else {
            nodo(child, "epsilon (vacio)");
        }
    }

    private void parseTermino(String indent) {
        nodo(indent, "<termino>");
        String child = indent + "|   ";
        parseFactor(child);
        parseTermPrima(child);
    }

    private void parseTermPrima(String indent) {
        nodo(indent, "<term_prima>");
        String child = indent + "|   ";
        if (actual().tipo == TipoToken.POR || actual().tipo == TipoToken.DIV) {
            consumir(actual().tipo, child);
            parseFactor(child);
            parseTermPrima(child);
        } else {
            nodo(child, "epsilon (vacio)");
        }
    }

    private void parseFactor(String indent) {
        nodo(indent, "<factor>");
        String child = indent + "|   ";
        Token t = actual();
        if (t.tipo == TipoToken.NUMERO || t.tipo == TipoToken.CADENA || t.tipo == TipoToken.IDENTIFICADOR) {
            consumir(t.tipo, child);
        } else if (t.tipo == TipoToken.PAR_IZQ) {
            consumir(TipoToken.PAR_IZQ, child);
            parseExpresion(child);
            consumir(TipoToken.PAR_DER, child);
        } else {
            throw new ErrorSintacticoException(
                "Linea " + t.linea + ": Se esperaba NUMERO, CADENA, IDENTIFICADOR o '(', pero se encontro " +
                t.tipo + " ('" + t.lexema + "')."
            );
        }
    }

    // --- PUNTO DE ENTRADA: LECTURA DE ARCHIVO FUENTE ---
    public static void main(String[] args) {
        String rutaArchivo = (args.length > 0) ? args[0] : "ejemplo_valido.scfg";
        Path path = Paths.get(rutaArchivo);

        try {
            if (!Files.exists(path) && args.length == 0) {
                String contenidoDefault =
                        "Inicio\n" +
                        "  Modelo = \"Supermercado_Norte\";\n" +
                        "  cajas_base = 3;\n" +
                        "  Servidores = cajas_base + 2;\n" +
                        "  TiempoLlegada = (10 + 2) / 3;\n" +
                        "  FilaMaxima = 40;\n" +
                        "  Distribucion = \"Exponencial\";\n" +
                        "  Unidad = \"Minutos\";\n" +
                        "Fin\n";
                Files.writeString(path, contenidoDefault, StandardCharsets.UTF_8);
            }

            String codigoFuente = Files.readString(path, StandardCharsets.UTF_8);

            System.out.println("========================================================");
            System.out.println("COMPILADOR FRONTEND SIMULCONFIG");
            System.out.println("Archivo fuente: " + path.toAbsolutePath());
            System.out.println("========================================================\n");

            // 1. Ejecutar analisis lexico
            ResultadoLexico resLexico = ejecutarFaseLexica(codigoFuente);
            if (!resLexico.exitoso) {
                System.out.println("\n[RESULTADO FINAL] COMPILACION ABORTADA EN FASE LEXICA.");
                System.out.println("Corrija los errores lexicos antes de continuar al analisis sintactico.");
                return;
            }
            System.out.println("[OK] Fase lexica completada sin errores.\n");

            // 2. Ejecutar analisis sintactico
            System.out.println("=== FASE 2: ANALISIS SINTACTICO ===");
            AnalizadorSimulConfig parser = new AnalizadorSimulConfig(resLexico.tokens);
            String arbolDerivacion = parser.analizarSintaxis();

            System.out.println("[RESULTADO FINAL] PROGRAMA SINTACTICAMENTE VALIDO.");
            System.out.println("\nArbol de derivacion generado:\n" + arbolDerivacion);

        } catch (ErrorSintacticoException ex) {
            System.out.println("[ERROR SINTACTICO] " + ex.getMessage());
            System.out.println("\n[RESULTADO FINAL] COMPILACION RECHAZADA EN FASE SINTACTICA.");
        } catch (IOException ex) {
            System.err.println("No se pudo leer el archivo fuente: " + rutaArchivo + " -> " + ex.getMessage());
        }
    }
}