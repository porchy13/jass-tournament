package ch.varani.jass.solver;

import com.google.ortools.Loader;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Loads the native libraries of OR-Tools before the first search.
 *
 * <p><b>Algorithm</b>: the packaged application ships the native libraries of its platform unpacked, in a directory
 * given by {@code java.library.path}, so that they are never extracted to a temporary directory at run time
 * (AGENTS.md §7). On macOS and Linux, {@link Loader#loadNativeLibraries()} then finds {@code jniortools} there, and
 * the system finds its dependencies next to it. Windows does not look for the dependencies of a library in its
 * directory: the libraries of OR-Tools are first loaded one by one from {@code java.library.path}, in the order of
 * their dependencies ({@link #WINDOWS_LIBRARIES}), as the loader of OR-Tools does from its temporary directory; a
 * library loaded once satisfies the dependencies of the next ones. When a mandatory library is not found (e.g. while
 * developing, without unpacked libraries), the preloading stops and {@link Loader#loadNativeLibraries()} falls back to
 * the libraries of the OR-Tools jar.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class NativeLibraries {

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * A native library of OR-Tools on Windows.
   *
   * @param name the name of the library, without prefix nor extension.
   * @param mandatory whether OR-Tools cannot run without it.
   */
  record Library(String name, boolean mandatory) {
  }

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The native libraries of OR-Tools on Windows, in the order of their dependencies. */
  static final List<Library> WINDOWS_LIBRARIES = List.of(
      new Library("zlib1", true), new Library("bz2", true), new Library("abseil_dll", true),
      new Library("re2", true), new Library("libutf8_validity", true), new Library("libprotobuf", true),
      new Library("highs", false), new Library("libscip", false), new Library("ortools", true),
      new Library("jniortools", true));

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private NativeLibraries() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Loads the native libraries of OR-Tools on the current system.
   */
  static void load() {
    load(System.getProperty("os.name"), System::loadLibrary, Loader::loadNativeLibraries);
  }

  /**
   * Loads the native libraries of OR-Tools: on Windows, preloads them from {@code java.library.path}, then lets the
   * loader of OR-Tools load them.
   *
   * @param system the name of the operating system, e.g. {@code Windows 11}.
   * @param library loads a library by its name from {@code java.library.path}; throws {@link UnsatisfiedLinkError}
   *                when it cannot.
   * @param loader the loader of OR-Tools.
   */
  static void load(final String system, final Consumer<String> library, final Runnable loader) {
    if (system.toLowerCase(Locale.ROOT).startsWith("windows")) {
      preload(library);
    }
    loader.run();
  }

  /**
   * Loads the libraries of OR-Tools on Windows in the order of their dependencies; an optional library not found is
   * skipped, a mandatory one stops the preloading.
   *
   * @param library loads a library by its name; throws {@link UnsatisfiedLinkError} when it cannot.
   */
  private static void preload(final Consumer<String> library) {
    for (final Library candidate : WINDOWS_LIBRARIES) {
      try {
        library.accept(candidate.name());
      } catch (UnsatisfiedLinkError _) {
        if (candidate.mandatory()) {
          return;
        }
      }
    }
  }

}
