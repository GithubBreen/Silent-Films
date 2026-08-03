package com.breenihilation.client;


import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// parakeet shit
final class ParakeetModelFiles {
	static final String DIRECTORY = "silentfilms/models/parakeet-tdt-0.6b-v3-int8";
	private static final String BASE_URL = "https://huggingface.co/csukuangfj/"
			+ "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/";
	static final List<ModelFile> FILES = List.of(
			new ModelFile("encoder.int8.onnx", 652_184_281L,
					"acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
			new ModelFile("decoder.int8.onnx", 11_845_275L,
					"179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
			new ModelFile("joiner.int8.onnx", 6_355_277L,
					"3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
			new ModelFile("tokens.txt", 93_939L,
					"d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d")
	);
	private ParakeetModelFiles() {
	}

	static Path directory(Path gameDirectory) {
		return gameDirectory.resolve(DIRECTORY);
	}

	static boolean installed(Path gameDirectory) {
		Path directory = directory(gameDirectory);
		return FILES.stream().allMatch(file -> {
			Path path = directory.resolve(file.filename());
			try {
				return Files.isRegularFile(path) && Files.size(path) == file.bytes();
			} catch (java.io.IOException ignored) {
				return false;
			}
		});
	}

	static long totalBytes() {
		return FILES.stream().mapToLong(ModelFile::bytes).sum();
	}

	record ModelFile(String filename, long bytes, String sha256) {
		URI downloadUri() {
			return URI.create(BASE_URL + filename + "?download=true");
		}
	}
}
