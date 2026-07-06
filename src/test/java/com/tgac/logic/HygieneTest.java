package com.tgac.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Guardrail: production code must not print to the console. Debug output belongs in the
 * box-model tracer (see debug/Trace.java), not scattered System.out calls. This caught a
 * real regression once; keep it green.
 */
public class HygieneTest {

	@Test
	public void mainCodeDoesNotPrintToTheConsole() throws IOException {
		Path mainRoot = Paths.get("src/main/java");
		try (Stream<Path> files = Files.walk(mainRoot)) {
			List<String> offenders = files
					.filter(p -> p.toString().endsWith(".java"))
					// Trace.printing() is a console tracer; printing is its whole purpose
					.filter(p -> !p.getFileName().toString().equals("Trace.java"))
					.filter(HygieneTest::printsToConsole)
					.map(p -> mainRoot.relativize(p).toString())
					.collect(Collectors.toList());

			assertThat(offenders)
					.as("production files printing to the console (use the tracer instead)")
					.isEmpty();
		}
	}

	private static boolean printsToConsole(Path file) {
		try {
			String src = new String(Files.readAllBytes(file));
			return src.contains("System.out") || src.contains("System.err");
		} catch (IOException e) {
			throw new RuntimeException("could not read " + file, e);
		}
	}
}
