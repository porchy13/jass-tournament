package ch.varani.jass.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link Target}: platform of a system, names of the installers.
 */
final class TargetTest {

  /**
   * Each system with a published installer has its platform; the others have none.
   */
  @Test
  @DisplayName("The platform comes from the name of the system and its architecture")
  void ofTest() {
    assertAll(
        () -> assertEquals(Optional.of(Target.MACOS_AARCH64), Target.of("Mac OS X", "aarch64")),
        () -> assertEquals(Optional.of(Target.MACOS_X64), Target.of("Mac OS X", "x86_64")),
        () -> assertEquals(Optional.of(Target.WINDOWS_X64), Target.of("Windows 11", "amd64")),
        () -> assertEquals(Optional.of(Target.LINUX_X64), Target.of("Linux", "amd64")),
        () -> assertEquals(Optional.empty(), Target.of("Linux", "aarch64")),
        () -> assertEquals(Optional.empty(), Target.of("Windows 11", "aarch64")),
        () -> assertEquals(Optional.empty(), Target.of("FreeBSD", "amd64")));
  }

  /**
   * The installers are named as the release workflow names them; the version is read back from the name.
   */
  @Test
  @DisplayName("The installer of a platform is named after the version and the platform")
  void installerTest() {
    final Version version = Version.parse("1.2.0");
    assertAll(
        () -> assertEquals("jass-tournament-1.2.0-macos-aarch64.dmg", Target.MACOS_AARCH64.installer(version)),
        () -> assertEquals("jass-tournament-1.2.0-windows-x64.msi", Target.WINDOWS_X64.installer(version)),
        () -> assertEquals("jass-tournament-1.2.0-linux-x64.deb", Target.LINUX_X64.installer(version)),
        () -> assertEquals(Optional.of(version), Target.MACOS_X64.versionOf("jass-tournament-1.2.0-macos-x64.dmg")),
        () -> assertEquals(Optional.empty(), Target.MACOS_X64.versionOf("jass-tournament-1.2.0-macos-aarch64.dmg")),
        () -> assertEquals(Optional.empty(), Target.MACOS_X64.versionOf("jass-tournament-1.2.0-macos-x64.dmgx")),
        () -> assertEquals(Optional.empty(), Target.MACOS_X64.versionOf("SHA256SUMS")));
  }

}
