package de.dasbabypixel.lwjgl.testing;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.LockSupport;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;
import org.lwjgl.glfw.GLFWWindowCloseCallbackI;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Configuration;

public class TestingContext2 {

	private static final boolean disableExplicitVersion = true;

	private static final GLFWThread glfw = new GLFWThread();

	private static final RenderThread render = new RenderThread();

	private static final AsyncWorker async = new AsyncWorker();

	private static int mode = 1; // 0 does work (it does not call glCopyImageSubData),
								 // 1 does not work (resizing can cause it to sometimes render, but very rarely
								 // and not the entire screen but just a part),
								 // 2 does not work (works at first, but breaks
								 // some time after resizing)

	public static void main(String[] args) throws InterruptedException {
		Configuration.OPENGLES_EXPLICIT_INIT.set(true);
		Configuration.OPENGL_EXPLICIT_INIT.set(true);

		GL.create(); // Hack to use GL ES. I want to do this so I only use ES functions to be able to
					 // port to android later without problems and recoding a lot of things

		glfw.start();
		glfw.latch.await(); // Wait for windows to be created
		async.start();
		render.start();

		render.join();
		async.join();
		glfw.exit = true;
		glfw.join();
		GL.destroy();
	}

	private static void createCapabilities() {
		GL.createCapabilities();
	}

	private static void destroyCapabilities() {
		GL.setCapabilities(null);
	}

	private static class AsyncWorker extends Thread {

		private CountDownLatch latch = new CountDownLatch(1);

		public AsyncWorker() {
			setName("AsyncWorker");
		}

		@Override
		public void run() {
			glfwMakeContextCurrent(glfw.secondary);
			createCapabilities();

			System.out.println(glGetInteger(GL_MAJOR_VERSION) + "." + glGetInteger(GL_MINOR_VERSION));

			if (mode == 2)
				render.phaser.awaitAdvance(render.phaser.arriveAndDeregister());

			ByteBuffer data = memAlloc(Integer.BYTES * 2 * 2);
			Color c = new Color(/* g */0.1F, /* b */0.2F, /* a */0.7F, /* r */0.9F);
			int rgba = c.getRGB();
			for (int i = 0; i < 2 * 2; i++) {
				data.putInt(rgba);
			}
			data.flip();

			int id1 = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, id1);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
			memFree(data);

			int id2 = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, id2);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 3, 3, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
			glBindTexture(GL_TEXTURE_2D, 0);

			if (mode != 0)
				glCopyImageSubData(id1, GL_TEXTURE_2D, 0, 0, 0, 0, id2, GL_TEXTURE_2D, 0, 0, 0, 0, 2, 2, 1);

			write(id1, "img1.png");
			write(id2, "img2.png");

			glDeleteTextures(id1);
			glDeleteTextures(id2);

			glFlush();
			glFinish();

			latch.countDown();

			destroyCapabilities();
			glfwMakeContextCurrent(0);
		}

		private void write(int tex, String name) {
			glBindTexture(GL_TEXTURE_2D, tex);
			int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
			int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TRANSLUCENT);
			ByteBuffer data = memAlloc(Integer.BYTES * w * h);
			glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);

			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int rgba = data.getInt((y * h + x) * Integer.BYTES);
					int r = (rgba & 0xff000000) >> 24;
					int g = (rgba & 0x00ff0000) >> 16;
					int b = (rgba & 0x0000ff00) >> 8;
					int a = rgba & 0x000000ff;

					int argb = a << 24 | r << 16 | g << 8 | b;
					img.setRGB(x, y, argb);
				}
			}
			
			try {
				ImageIO.write(img, "png", new File(name));
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			memFree(data);
			glBindTexture(GL_TEXTURE_2D, 0);
		}

	}

	private static class RenderThread extends Thread {

		private volatile boolean exit = false;

		private volatile boolean render = true;

		private final Phaser phaser = new Phaser(Math.max(1, mode));

		public RenderThread() {
			setName("RenderThread");
		}

		@Override
		public void run() {
			glfwMakeContextCurrent(glfw.mainWindow);
			createCapabilities();
			if (mode == 1) { // wait for AsyncWorker to finish in mode 1, because it only breaks after the
							 // glCopyImageSubData call, so everything we render before works
				try {
					async.latch.await();
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
			while (!exit) {

				// Only draw when necessary
				if (!render)
					LockSupport.park();
				render = false;

				glViewport(0, 0, glfw.fbwidth, glfw.fbheight);
				glClearColor(1F, 0F, 0F, 0.5F);
				glClear(GL_COLOR_BUFFER_BIT);
				glfwSwapBuffers(glfw.mainWindow);

				phaser.arriveAndAwaitAdvance(); // Used for mode 2, where we synchronize this
			}
			destroyCapabilities();
			glfwMakeContextCurrent(0);
		}

	}

	private static class GLFWThread extends Thread {

		private volatile boolean exit = false;

		private final CountDownLatch latch = new CountDownLatch(1);

		private volatile long mainWindow;

		private volatile long secondary;

		private volatile int fbwidth;

		private volatile int fbheight;

		public GLFWThread() {
			setName("GLFW-Thread");
		}

		@Override
		public void run() {
			glfwInit();

			GLFWErrorCallback.createPrint().set();

			// Window hints to use opengl es and core profile
			glfwDefaultWindowHints();
			glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
			glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);

			if (!disableExplicitVersion) {
				// These two lines are part of the problem, remove them and it works.
				glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
				glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
			}

			mainWindow = glfwCreateWindow(400, 400, "testing", 0, 0);
			secondary = glfwCreateWindow(1, 1, "unused", 0, mainWindow);

			glfwSetFramebufferSizeCallback(mainWindow, new GLFWFramebufferSizeCallbackI() {

				@Override
				public void invoke(long window, int width, int height) {
					fbwidth = width;
					fbheight = height;

					// Signal redraw
					render.render = true;
					LockSupport.unpark(render);
				}

			});
			glfwSetWindowCloseCallback(mainWindow, new GLFWWindowCloseCallbackI() {

				@Override
				public void invoke(long window) {
					render.exit = true;
					render.render = true;
					LockSupport.unpark(render);
				}

			});
			IntBuffer w = memAllocInt(1);
			IntBuffer h = memAllocInt(1);
			glfwGetFramebufferSize(mainWindow, w, h);
			fbwidth = w.get(0);
			fbheight = h.get(0);
			memFree(w);
			memFree(h);

			glfwShowWindow(mainWindow);

			latch.countDown(); // Let other threads work

			while (!exit) {
				glfwWaitEventsTimeout(0.1D); // With timeout to allow exit even on no new events. glfwPostEmptyEvent is
											 // bugged idk why
				glfwPollEvents();
			}
			glfwDestroyWindow(mainWindow);
			glfwDestroyWindow(secondary);
			glfwTerminate();
		}

	}

}