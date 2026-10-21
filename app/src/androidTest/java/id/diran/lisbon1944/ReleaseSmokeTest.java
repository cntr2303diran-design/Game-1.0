package id.diran.lisbon1944;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Uji asap pada APK RILIS (R8) di emulator. Ditulis dalam Java + UiAutomator agar kode uji tidak bergantung pada
 * kelas aplikasi yang sudah diobfuskasi. Memeriksa: splash → menu → permainan baru → lempar dadu → kembali ke
 * menu (simpan otomatis, serialisasi kotlinx di bawah R8) → Lanjutkan (muat).
 */
@RunWith(AndroidJUnit4.class)
public class ReleaseSmokeTest {
    private static final String PKG = "id.diran.lisbon1944";
    private static final long LONG = 60_000;

    private UiDevice device;
    private File shots;

    private void shot(String name) {
        device.waitForIdle();
        device.takeScreenshot(new File(shots, name + ".png"));
    }

    private UiObject2 waitFor(BySelector sel, long ms, String what) {
        UiObject2 o = device.wait(Until.findObject(sel), ms);
        if (o == null) {
            shot("fail-" + what);
        }
        assertNotNull("Tidak menemukan: " + what, o);
        return o;
    }

    private static BySelector text(String regex) {
        return By.text(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
    }

    @Test
    public void playSaveAndContinueOnReleaseBuild() throws Exception {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        shots = new File(ctx.getExternalFilesDir(null), "shots");
        //noinspection ResultOfMethodCallIgnored
        shots.mkdirs();

        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(PKG);
        assertNotNull(launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(launch);
        device.wait(Until.hasObject(By.pkg(PKG).depth(0)), LONG);
        Thread.sleep(700);
        shot("e01-splash");

        UiObject2 newGame = waitFor(text("(NEW GAME|PERMAINAN BARU)"), LONG, "menu");
        shot("e02-menu");
        newGame.click();

        waitFor(text("(Seat 1|Kursi 1)"), LONG, "setup");
        shot("e03-setup");
        new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().textMatches("(?i).*(BEGIN OPERATION|MULAI OPERASI).*"));
        waitFor(text("(BEGIN OPERATION|MULAI OPERASI)"), LONG, "tombol mulai").click();

        // Animasi persiapan (bisa dilewati dengan ketukan).
        UiObject2 skip = device.wait(Until.findObject(text("(Tap to skip|Ketuk untuk melewati)")), 10_000);
        if (skip != null) {
            shot("e04-intro");
            skip.click();
        }

        UiObject2 roll = waitFor(text("(ROLL DICE|LEMPAR DADU)"), 180_000, "lempar dadu");
        shot("e05-game");
        roll.click();
        Thread.sleep(2500);
        shot("e06-after-roll");

        // Kembali ke menu → simpan otomatis.
        device.pressBack();
        waitFor(text("(TO MENU|KE MENU)"), LONG, "dialog keluar").click();
        UiObject2 cont = waitFor(text("(CONTINUE|LANJUTKAN)"), LONG, "tombol lanjutkan");
        shot("e07-menu-continue");
        cont.click();

        // Permainan termuat dari berkas simpanan (kotlinx.serialization di bawah R8).
        assertTrue("Permainan tidak termuat", device.wait(Until.hasObject(text("(Act|Aksi).*")), LONG));
        Thread.sleep(1500);
        shot("e08-continued");
    }
}
