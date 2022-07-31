package de.dasbabypixel.lwjgl.testing;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengles.GLES20.*;
import static org.lwjgl.opengles.GLES32.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallbackI;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengles.GLES;
import org.lwjgl.system.Configuration;

@SuppressWarnings("javadoc")
public class TestingContext {

	private static int width = 400;

	private static int height = 400;

	private static boolean sizeChange = true;

	public static void main(String[] args) throws IOException {
		glfwInit();

		Configuration.OPENGLES_EXPLICIT_INIT.set(true);
		Configuration.OPENGL_EXPLICIT_INIT.set(true);

		GL.create();
		GLES.create(GL.getFunctionProvider());
		GL.destroy();

		AtomicInteger texRef = new AtomicInteger();

		GLFWErrorCallback.createPrint().set();
		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);
		glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_ES_API);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		long window = glfwCreateWindow(width, height, "Testing", 0, 0);
		glfwSetWindowSizeCallback(window, new GLFWWindowSizeCallbackI() {

			@Override
			public void invoke(long window, int width, int height) {
				TestingContext.width = width;
				TestingContext.height = height;
				sizeChange = true;
			}

		});
		long window2 = glfwCreateWindow(1, 1, "unused", 0, window);
		glfwMakeContextCurrent(window);
		GLES.createCapabilities();

//		int program = glCreateProgram();
//		int vs = glCreateShader(GL_VERTEX_SHADER);
//		glShaderSource(vs, load("vertex"));
//		glCompileShader(vs);
//		if (glGetShaderi(vs, GL_COMPILE_STATUS) == 0) {
//			throw new RuntimeException("Error compiling Shader code: " + glGetShaderInfoLog(vs, 1024));
//		}
//		glAttachShader(program, vs);
//		glDeleteShader(vs);
//		int fs = glCreateShader(GL_FRAGMENT_SHADER);
//		glShaderSource(fs, load("fragment"));
//		glCompileShader(fs);
//		if (glGetShaderi(fs, GL_COMPILE_STATUS) == 0) {
//			throw new RuntimeException("Error compiling Shader code: " + glGetShaderInfoLog(fs, 1024));
//		}
//		glAttachShader(program, fs);
//		glDeleteShader(fs);
//		glLinkProgram(program);
//		glValidateProgram(program);
//		if (glGetProgrami(program, GL_VALIDATE_STATUS) == 0) {
//			Logger.getLogger("testing").severe("Warning validating Shader code: " + glGetProgramInfoLog(program, 1024));
//		}
//
//		glUseProgram(program);
//
//		int texture = glGenTextures();
//		texRef.set(texture);
//		ByteBuffer pixels = memAlloc(4);
//		pixels.putInt(new Color(0.5F, 1F, 0F, 0.2F).getRGB());
//		pixels.flip();
//		glBindTexture(GL_TEXTURE_2D, texture);
//		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
//		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
//		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
//		memFree(pixels);

		CountDownLatch l1 = new CountDownLatch(1);
		CountDownLatch l2 = new CountDownLatch(1);

		Thread th = new Thread(() -> {
			boolean first = true;
			glfwMakeContextCurrent(window2);
			GLES.createCapabilities();
			try {
				l1.await();
			} catch (InterruptedException ex1) {
				ex1.printStackTrace();
			}
			while (true) {
				ByteBuffer p = memAlloc(4);
				Random r = new Random();
				p.put((byte) r.nextInt());
				p.put((byte) r.nextInt());
				p.put((byte) r.nextInt());
				p.put((byte) r.nextInt());
				p.flip();
				int id = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, id);
				glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 2, 2, 0, GL_ALPHA, GL_UNSIGNED_BYTE, p);
				glBindTexture(GL_TEXTURE_2D, 0);
				memFree(p);

				int id2 = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, id2);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 3, 3, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
				glBindTexture(GL_TEXTURE_2D, 0);

				glCopyImageSubData(id, GL_TEXTURE_2D, 0, 0, 0, 0, id2, GL_TEXTURE_2D, 0, 0, 0, 0, 2, 2, 1);

				try {
					ImageIO.write(getBufferedImage(id, 2, 2), "png", new File("img1.png"));
					ImageIO.write(getBufferedImage(id2, 3, 3), "png", new File("img2.png"));
				} catch (IOException ex) {
					ex.printStackTrace();
				}
				glDeleteTextures(id);

				int old = texRef.getAndSet(id2);
				if (!first) {
					glDeleteTextures(old);
				} else {
					first = false;
				}
				l2.countDown();
				break;
			}
			glfwMakeContextCurrent(0);
			GLES.setCapabilities(null);
		});
		th.start();

//		// @formatter:off
//		int vao = glGenVertexArrays();
//		glBindVertexArray(vao);
//		int buf1 = glGenBuffers();
//		glBindBuffer(GL_ARRAY_BUFFER, buf1);
//		glBufferData(GL_ARRAY_BUFFER, new float[] {
//				-0.5F, -0.5F, 0F,
//				-0.5F, 0.5F, 0F,
//				0.5F, 0.5F, 0F,
//				0.5F, -0.5F, 0F
//		}, GL_STATIC_DRAW);
//		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
//		
//		int buf2 = glGenBuffers();
//		glBindBuffer(GL_ARRAY_BUFFER, buf2);
//		glBufferData(GL_ARRAY_BUFFER, new float[] {
//				0, 1,
//				0, 0,
//				1, 0,
//				1, 1
//		}, GL_STATIC_DRAW);
//		glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
//		
//		int buf3 = glGenBuffers();
//		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buf3);
//		glBufferData(GL_ELEMENT_ARRAY_BUFFER, new int[] {
//				0, 2, 1,
//				0, 3, 2
//		}, GL_STATIC_DRAW);
//		
//		// @formatter:on
//
//		FloatBuffer tmp = memAllocFloat(16);
//
//		Matrix4f proj = new Matrix4f();
//		Matrix4f model = new Matrix4f();
//		model.identity();
//		model.translate(200, 200, 0);
//		model.scale(200);

		long longestFrameTimeNanos = 0L;
		long count = 0L;
		long avg = -1L;
		long lastDisplay = System.nanoTime();

		boolean visible = false;

//		glfwShowWindow(window);
		while (!glfwWindowShouldClose(window)) {
			count++;
			long start = System.nanoTime();
//			if (sizeChange) {
//				project(proj);
//				sizeChange = false;
//			}

			glViewport(0, 0, width, height);
			l1.countDown();
			try {
				l2.await();
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
			glClearColor(1F, 0, 0, 0.5F);
			glClear(GL_COLOR_BUFFER_BIT);

//			glMatrixMode(GL_PROJECTION);
//			glLoadIdentity();
//			glOrtho(0, width, height, 0, -1, 1);

//			glActiveTexture(GL_TEXTURE0);
//			glBindTexture(GL_TEXTURE_2D, texRef.get());
//
//			set(tmp, proj);
//			glUniformMatrix4fv(glGetUniformLocation(program, "projectionMatrix"), false, tmp);
//			set(tmp, model);
//			glUniformMatrix4fv(glGetUniformLocation(program, "modelMatrix"), false, tmp);
//			glUniform4f(glGetUniformLocation(program, "color"), 1, 1, 1, 1);
//
//			glEnableVertexAttribArray(0);
//			glEnableVertexAttribArray(1);
//			glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
//			glDisableVertexAttribArray(0);
//			glDisableVertexAttribArray(1);

//			glfwWaitEvents();
			glfwPollEvents();
			if (visible == false) {
				visible = true;
				glfwShowWindow(window);
			}
			glfwSwapBuffers(window);
			long end = System.nanoTime();
			long time = end - start;
			if (time > longestFrameTimeNanos) {
				longestFrameTimeNanos = time;
				System.out.printf("%sns - %sms%n", time, TimeUnit.NANOSECONDS.toMillis(time));
			}

			if (count == 1) {
				avg = time;
			} else {
				avg = (avg * (count - 1) + time) / count;
			}

			if (System.nanoTime() - lastDisplay > TimeUnit.SECONDS.toNanos(1)) {
				System.out.printf("Avg: %sns - %.2fms%n", avg, avg / 1000000F);
				lastDisplay = System.nanoTime();
			}
		}

//		memFree(tmp);
//
//		glDeleteBuffers(buf3);
//		glDeleteBuffers(buf2);
//		glDeleteBuffers(buf1);
//		glDeleteVertexArrays(vao);
//
//		glDeleteProgram(program);
		glfwDestroyWindow(window2);
		glfwDestroyWindow(window);
		glfwTerminate();

		GL.destroy();
	}

	public static BufferedImage getBufferedImage(int texture, int width, int height) {
		ByteBuffer pixels = getBufferedImageBuffer(texture, width, height);
		IntBuffer ipixels = pixels.asIntBuffer();
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TRANSLUCENT);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				img.setRGB(x, y, ipixels.get(y * width + x));
			}
		}
		memFree(pixels);
		return img;
	}

	private static ByteBuffer getBufferedImageBuffer(int texture, int width, int height) {
		ByteBuffer pixels = memAlloc(4 * width * height);
		glBindTexture(GL_TEXTURE_2D, texture);
		int fbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glDeleteFramebuffers(fbo);
		glBindTexture(GL_TEXTURE_2D, 0);
		return pixels;
	}

	public static void set(FloatBuffer buf, float m00, float m01, float m02, float m03, float m10, float m11, float m12,
			float m13, float m20, float m21, float m22, float m23, float m30, float m31, float m32, float m33) {
		buf.put(0, m00)
				.put(1, m01)
				.put(2, m02)
				.put(3, m03)
				.put(4, m10)
				.put(5, m11)
				.put(6, m12)
				.put(7, m13)
				.put(8, m20)
				.put(9, m21)
				.put(10, m22)
				.put(11, m23)
				.put(12, m30)
				.put(13, m31)
				.put(14, m32)
				.put(15, m33);
	}

	public static void set(FloatBuffer buf, Matrix4f m) {
		set(buf, m.m00(), m.m01(), m.m02(), m.m03(), m.m10(), m.m11(), m.m12(), m.m13(), m.m20(), m.m21(), m.m22(),
				m.m23(), m.m30(), m.m31(), m.m32(), m.m33());
	}

	private static void project(Matrix4f mat) {
		mat.setOrtho(0, width, 0, height, -10000, 10000);
	}

	public static class LoaderThread {

	}

	private static String load(String path) throws IOException {
		InputStream in = TestingContext.class.getClassLoader().getResourceAsStream(path);
		byte[] a = read(in, 1);
		in.close();
		return new String(a, StandardCharsets.UTF_8);
	}

	private static byte[] read(InputStream source, int initialSize) throws IOException {
		int capacity = initialSize;
		byte[] buf = new byte[capacity];
		int nread = 0;
		int n;
		int BUFFER_SIZE = 8192;
		int MAX_BUFFER_SIZE = Integer.MAX_VALUE;
		for (;;) {
			// read to EOF which may read more or less than initialSize (eg: file
			// is truncated while we are reading)
			while ((n = source.read(buf, nread, capacity - nread)) > 0)
				nread += n;

			// if last call to source.read() returned -1, we are done
			// otherwise, try to read one more byte; if that failed we're done too
			if (n < 0 || (n = source.read()) < 0)
				break;

			// one more byte was read; need to allocate a larger buffer
			if (capacity <= MAX_BUFFER_SIZE - capacity) {
				capacity = Math.max(capacity << 1, BUFFER_SIZE);
			} else {
				if (capacity == MAX_BUFFER_SIZE)
					throw new OutOfMemoryError("Required array size too large");
				capacity = MAX_BUFFER_SIZE;
			}
			buf = Arrays.copyOf(buf, capacity);
			buf[nread++] = (byte) n;
		}
		return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
	}

}
