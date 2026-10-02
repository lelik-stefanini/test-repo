package app.supernaut.fx.sample.maven.steps;

import app.supernaut.fx.FxLauncher;
import app.supernaut.fx.sample.maven.HelloFX;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the real SupernautFX + Micronaut launcher used by {@link HelloFX#main}
 * and inspects the resulting JavaFX window, so the scenario exercises the same
 * startup path as running the application.
 */
public class ApplicationStartupSteps {

    private Thread launcherThread;
    private Stage applicationStage;

    @When("the HelloFX application is launched")
    public void the_hellofx_application_is_launched() {
        launcherThread = new Thread(
                () -> FxLauncher.byName("micronaut").launch(new String[0], HelloFX.class),
                "HelloFX-Test-Launcher");
        launcherThread.setDaemon(true);
        launcherThread.start();
    }

    @Then("the application window becomes visible within {int} seconds")
    public void the_application_window_becomes_visible_within_seconds(int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            applicationStage = findVisibleStage();
            if (applicationStage != null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No visible application window appeared within " + timeoutSeconds + " seconds");
    }

    @And("the window title is {string}")
    public void the_window_title_is(String expectedTitle) throws InterruptedException {
        String actualTitle = callOnFxThread(() -> applicationStage.getTitle());
        if (!expectedTitle.equals(actualTitle)) {
            throw new AssertionError("Expected window title \"" + expectedTitle + "\" but was \"" + actualTitle + "\"");
        }
    }

    @And("the window shows a greeting containing {string}")
    public void the_window_shows_a_greeting_containing(String expectedText) throws InterruptedException {
        String labelText = callOnFxThread(() -> {
            Label label = findLabel(applicationStage.getScene().getRoot());
            return label == null ? null : label.getText();
        });
        if (labelText == null || !labelText.contains(expectedText)) {
            throw new AssertionError("Expected a label containing \"" + expectedText + "\" but found: " + labelText);
        }
    }

    @After
    public void shutDownApplication() throws InterruptedException {
        try {
            Platform.runLater(() -> {
                if (applicationStage != null) {
                    applicationStage.hide();
                }
                Platform.exit();
            });
        } catch (IllegalStateException toolkitNotRunning) {
            // the JavaFX application never finished starting up; nothing to shut down
        }
        if (launcherThread != null) {
            launcherThread.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private static Stage findVisibleStage() throws InterruptedException {
        try {
            return callOnFxThread(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof Stage && window.isShowing()) {
                        return (Stage) window;
                    }
                }
                return null;
            });
        } catch (IllegalStateException toolkitNotYetInitialized) {
            return null;
        }
    }

    private static Label findLabel(Node node) {
        if (node instanceof Label) {
            return (Label) node;
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                Label found = findLabel(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T callOnFxThread(java.util.function.Supplier<T> supplier) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            result.set(supplier.get());
            latch.countDown();
        });
        latch.await(5, TimeUnit.SECONDS);
        return result.get();
    }
}
