package com.gama.interpreter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Gamajlox {
    private static boolean hadError;
    private static boolean hadRuntimeError;
    private static Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: gamajlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        while (true) {
            System.out.print("> ");
            run(reader.readLine(), true);
            hadError = false;
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()), false);
        if (hadError) {
            System.exit(65);
        } else if (hadRuntimeError) {
            System.exit(70);
        }
    }

    private static void run(String source, boolean isReplMode) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens, isReplMode);
        List<Stmt> ast = parser.parse();

        if (hadError) {
            return;
        }

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(ast);

        if (hadError) {
            return;
        }

        interpreter.interpret(ast);
    }

    static void warning(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message, false);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message, false);
        }
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message, true);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message, true);
        }
    }

    static void error(int line, String message) {
        report(line, "", message, true);
    }

    private static void report(int line, String where, String message, boolean isError) {
        String messageSeverity = isError ? "Error" : "Warning";
        System.err.println("[line " + line + "] " + messageSeverity + where + ": " + message);
        if (isError) {
            hadError = true;
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }
}
