package ch.varani.jass.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link NativeLibraries}: the libraries of OR-Tools are preloaded in the order of their dependencies on
 * Windows only, an optional library may be missing, a missing mandatory one leaves the loading to OR-Tools.
 */
final class NativeLibrariesTest {

  /**
   * On Windows, every library is loaded in the order of its dependencies, then the loader of OR-Tools runs; elsewhere,
   * only the loader of OR-Tools runs.
   */
  @Test
  @DisplayName("The libraries of OR-Tools are preloaded in order on Windows only")
  void orderTest() {
    final List<String> windows = new ArrayList<>();
    final List<String> mac = new ArrayList<>();
    NativeLibraries.load("Windows 11", windows::add, () -> windows.add("loader"));
    NativeLibraries.load("Mac OS X", mac::add, () -> mac.add("loader"));
    final List<String> expected = new ArrayList<>(NativeLibraries.WINDOWS_LIBRARIES.stream()
                                                                                   .map(NativeLibraries.Library::name)
                                                                                   .toList());
    expected.add("loader");
    assertAll(
        () -> assertEquals(expected, windows),
        () -> assertEquals(List.of("loader"), mac),
        () -> assertEquals("jniortools", NativeLibraries.WINDOWS_LIBRARIES.getLast().name()));
  }

  /**
   * A missing optional library is skipped; a missing mandatory library stops the preloading, and the loader of
   * OR-Tools still runs (it falls back to the libraries of its jar).
   */
  @Test
  @DisplayName("A missing optional library is skipped, a missing mandatory one stops the preloading")
  void missingTest() {
    final List<String> optional = new ArrayList<>();
    NativeLibraries.load("windows 10", name -> {
      if (name.equals("highs")) {
        throw new UnsatisfiedLinkError(name);
      }
      optional.add(name);
    }, () -> optional.add("loader"));
    final List<String> mandatory = new ArrayList<>();
    NativeLibraries.load("Windows 11", name -> {
      if (name.equals("bz2")) {
        throw new UnsatisfiedLinkError(name);
      }
      mandatory.add(name);
    }, () -> mandatory.add("loader"));
    assertAll(
        () -> assertEquals(NativeLibraries.WINDOWS_LIBRARIES.size(), optional.size()),
        () -> assertEquals(List.of("zlib1", "loader"), mandatory));
  }

  /**
   * On the current system, the libraries of OR-Tools are loaded.
   */
  @Test
  @DisplayName("The libraries of OR-Tools load on the current system")
  void loadTest() {
    assertDoesNotThrow(() -> NativeLibraries.load());
  }

}
