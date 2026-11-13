package net.mcreator.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads Effekseer's versioned INFO chunk, without decoding or changing its runtime effect. */
public final class EffekseerAssetReferences {

	private EffekseerAssetReferences() {}

	public static Set<String> read(Path file) throws IOException {
		try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
			if (input.length() < 16 || input.readInt() != 0x45464b45)
				throw new IOException("Not an Effekseer EFKE file: " + file.getFileName());
			input.skipBytes(4);
			byte[] info = null;
			boolean runtime = false;
			while (input.getFilePointer() < input.length()) {
				if (input.length() - input.getFilePointer() < 8)
					throw new IOException("Truncated Effekseer chunk");
				int tag = input.readInt();
				int length = Integer.reverseBytes(input.readInt());
				if (length < 0 || length > input.length() - input.getFilePointer())
					throw new IOException("Invalid Effekseer chunk length");
				if (tag == 0x494e464f) {
					if (length > 2 * 1024 * 1024)
						throw new IOException("Effekseer dependency list is too large");
					info = new byte[length];
					input.readFully(info);
				} else {
					if (tag == 0x42494e5f)
						runtime = length > 0;
					input.seek(input.getFilePointer() + length);
				}
			}
			if (info == null || !runtime)
				throw new IOException("Effect needs INFO and BIN_ chunks. Export it as .efkefc in Effekseer.");
			return parse(ByteBuffer.wrap(info).order(ByteOrder.LITTLE_ENDIAN));
		} catch (java.nio.BufferUnderflowException | IllegalArgumentException e) {
			throw new IOException("Invalid Effekseer dependency list: " + file.getFileName(), e);
		}
	}

	private static Set<String> parse(ByteBuffer buffer) throws IOException {
		Set<String> paths = new LinkedHashSet<>();
		int version = buffer.getInt();
		if (version < 1500) {
			buffer.position(0);
			version = 0;
		}
		if (version < 1700) {
			int groups = version >= 1610 ? 7 : version >= 1500 ? 6 : 5;
			for (int group = 0; group < groups; group++)
				for (int i = count(buffer); i > 0; i--)
					paths.add(path(buffer));
		} else {
			for (int i = count(buffer); i > 0; i--) {
				buffer.getInt(); // Resource type and usage flags precede each UTF-16 path.
				buffer.getInt();
				paths.add(path(buffer));
			}
		}
		return paths;
	}

	private static int count(ByteBuffer buffer) throws IOException {
		int count = buffer.getInt();
		if (count < 0 || count > 1024)
			throw new IOException("Invalid Effekseer dependency count");
		return count;
	}

	private static String path(ByteBuffer buffer) throws IOException {
		int characters = buffer.getInt();
		if (characters < 1 || characters > 32768 || characters * 2 > buffer.remaining())
			throw new IOException("Invalid Effekseer dependency path length");
		byte[] bytes = new byte[(characters - 1) * 2];
		buffer.get(bytes);
		if (buffer.getChar() != 0)
			throw new IOException("Unterminated Effekseer dependency path");
		String path = new String(bytes, StandardCharsets.UTF_16LE).replace('\\', '/');
		if (path.isBlank() || path.startsWith("/") || path.contains(":") || path.indexOf('\0') >= 0
				|| java.util.Arrays.asList(path.split("/")).contains(".."))
			throw new IOException("Export effect assets with relative paths inside its folder: " + path);
		return path;
	}
}
