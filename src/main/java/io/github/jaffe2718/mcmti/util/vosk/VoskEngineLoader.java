package io.github.jaffe2718.mcmti.util.vosk;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.jaffe2718.mcmti.MicrophoneTextInput;

/**
 * Creates {@link VoskEngine} instances in a class loader that is isolated from
 * RetroFuturaBootstrap (RFB).
 * <p>
 * RFB rewrites {@code java.lang.reflect.Field.set} calls in every class it
 * loads into a redirector that runs in the bootstrap class loader. JNA builds
 * its internal {@code Structure$FFIType} through such a call, and the
 * redirector cannot access the (public) field because it lives in a different
 * class loader:
 *
 * <pre>
 * IllegalAccessException: class ...UnsafeReflectionRedirector cannot access
 * a member of class com.sun.jna.Structure$FFIType with modifiers "public"
 * </pre>
 *
 * Loading Vosk and JNA in a child-first {@link URLClassLoader} means RFB never
 * transforms them, so JNA's normal reflective access works again. It also
 * isolates the bundled JNA from JNA copies shipped by other mods.
 */
public final class VoskEngineLoader {

    private static final String ENGINE_CLASS = "io.github.jaffe2718.mcmti.util.vosk.IsolatedVoskEngine";
    private static final String[] ISOLATED_PREFIXES = { "org.vosk.", "com.sun.jna." };

    private static ChildFirstClassLoader loader;

    private VoskEngineLoader() {}

    public static synchronized VoskEngine create() throws Exception {
        if (loader == null) {
            loader = new ChildFirstClassLoader(collectUrls(), VoskEngine.class.getClassLoader());
            MicrophoneTextInput.LOGGER.info(
                "Created isolated Vosk class loader with {} URL(s), first={}",
                loader.getURLs().length,
                loader.getURLs().length > 0 ? loader.getURLs()[0] : "none");
        }
        // Make sure JNA (which is initialised together with the engine class)
        // decodes the UTF-8 strings returned by Vosk correctly.
        String previousEncoding = System.getProperty("jna.encoding");
        System.setProperty("jna.encoding", "UTF-8");
        try {
            Class<?> engineClass = Class.forName(ENGINE_CLASS, true, loader);
            return (VoskEngine) engineClass.getDeclaredConstructor()
                .newInstance();
        } finally {
            if (previousEncoding == null) {
                System.clearProperty("jna.encoding");
            } else {
                System.setProperty("jna.encoding", previousEncoding);
            }
        }
    }

    private static URL[] collectUrls() {
        Map<String, URL> urls = new LinkedHashMap<String, URL>();
        addCodeSource(urls, VoskEngineLoader.class);
        addModJar(urls);
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            try {
                File file = new File(entry);
                if (file.exists()) {
                    urls.put(
                        file.getAbsolutePath(),
                        file.toURI()
                            .toURL());
                }
            } catch (Throwable ignored) {
                // ignore broken class path entries
            }
        }
        return urls.values()
            .toArray(new URL[0]);
    }

    /**
     * The mod jar itself is normally not part of {@code java.class.path}
     * (Forge adds mod jars through its own class loader), so locate it through
     * a known resource and add it explicitly.
     */
    private static void addModJar(Map<String, URL> urls) {
        try {
            URL marker = VoskEngineLoader.class.getResource("/assets/mcmti/lang/en_US.lang");
            if (marker != null) {
                String external = marker.toExternalForm();
                int separator = external.indexOf("!/");
                if (external.startsWith("jar:") && separator > 4) {
                    URL jarUrl = new URL(external.substring(4, separator));
                    urls.put(jarUrl.toExternalForm(), jarUrl);
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static void addCodeSource(Map<String, URL> urls, Class<?> type) {
        try {
            if (type.getProtectionDomain() != null && type.getProtectionDomain()
                .getCodeSource() != null) {
                URL location = type.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
                if (location != null) {
                    urls.put(location.toExternalForm(), location);
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static boolean isIsolated(String name) {
        if (ENGINE_CLASS.equals(name)) {
            return true;
        }
        for (String prefix : ISOLATED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {

        ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && isIsolated(name)) {
                    // Never fall back to the parent for Vosk/JNA: the parent
                    // class loader is transformed by RetroFuturaBootstrap and
                    // its JNA would fail with IllegalAccessException.
                    loaded = findClass(name);
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
