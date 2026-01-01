package com.example.jtorrent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandHandler Tests")
class CommandHandlerTest {

    @Test
    @DisplayName("Create command handler")
    void testCreateCommandHandler() {
        // CommandHandler is a functional interface
        CommandHandler handler = (args) -> {
            // Do nothing
        };
        assertNotNull(handler);
    }

    @Test
    @DisplayName("Execute command handler")
    void testExecuteCommandHandler() {
        final boolean[] executed = { false };
        CommandHandler handler = (args) -> {
            executed[0] = true;
        };

        CliArgs args = new CliArgs();
        args.input = "test.torrent";

        handler.execute(args);
        assertTrue(executed[0]);
    }

    @Test
    @DisplayName("Command handler with multiple operations")
    void testCommandHandlerMultipleOperations() {
        final int[] counter = { 0 };
        CommandHandler handler = (args) -> {
            if (args.input != null)
                counter[0]++;
            if (args.output != null)
                counter[0]++;
        };

        CliArgs args = new CliArgs();
        args.input = "test.torrent";
        args.output = "/downloads";

        handler.execute(args);
        assertEquals(2, counter[0]);
    }

    @Test
    @DisplayName("Command handler with null args")
    void testCommandHandlerNullArgs() {
        CommandHandler handler = (args) -> {
            assertNotNull(args);
        };

        CliArgs args = new CliArgs();
        assertDoesNotThrow(() -> handler.execute(args));
    }

    @Test
    @DisplayName("Lambda command handler")
    void testLambdaCommandHandler() {
        CommandHandler handler = args -> System.out.println("Executing command");
        assertNotNull(handler);
    }
}
