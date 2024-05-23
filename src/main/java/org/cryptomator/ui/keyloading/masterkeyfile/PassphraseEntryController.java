package org.cryptomator.ui.keyloading.masterkeyfile;

import com.fazecast.jSerialComm.SerialPort;
import com.google.common.io.BaseEncoding;
import org.apache.commons.lang3.ArrayUtils;
import org.cryptomator.common.Nullable;
import org.cryptomator.common.Passphrase;
import org.cryptomator.common.keychain.KeychainManager;
import org.cryptomator.common.vaults.Vault;
import org.cryptomator.ui.common.FxController;
import org.cryptomator.ui.common.WeakBindings;
import org.cryptomator.ui.controls.NiceSecurePasswordField;
import org.cryptomator.ui.forgetpassword.ForgetPasswordComponent;
import org.cryptomator.ui.keyloading.KeyLoading;
import org.polito.hsm.HardwareDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@PassphraseEntryScoped
public class PassphraseEntryController implements FxController {

	private static final Logger LOG = LoggerFactory.getLogger(PassphraseEntryController.class);

	private final Stage window;
	private final Vault vault;
	private final CompletableFuture<PassphraseEntryResult> result;
	private final Passphrase savedPassword;
	private final ForgetPasswordComponent.Builder forgetPassword;
	private final KeychainManager keychain;
	private final StringBinding vaultName;
	private final BooleanProperty unlockInProgress = new SimpleBooleanProperty();
	private final ObjectBinding<ContentDisplay> unlockButtonContentDisplay = Bindings.when(unlockInProgress).then(ContentDisplay.LEFT).otherwise(ContentDisplay.TEXT_ONLY);
	private final BooleanProperty unlockButtonDisabled = new SimpleBooleanProperty();
	private  IntegerProperty randomKeyNumber;
	/* FXML */
	public NiceSecurePasswordField passwordField;
	public CheckBox savePasswordCheckbox;
	public ImageView face;
	public ImageView leftArm;
	public ImageView rightArm;
	public ImageView legs;
	public ImageView body;
	public Animation unlockAnimation;

	@Inject
	public PassphraseEntryController(@KeyLoading Stage window, @KeyLoading Vault vault, CompletableFuture<PassphraseEntryResult> result, @Nullable @Named("savedPassword") Passphrase savedPassword, ForgetPasswordComponent.Builder forgetPassword, KeychainManager keychain) {
		this.window = window;
		this.vault = vault;
		this.randomKeyNumber = this.vault.getVaultSettings().hwKeyNumber;
		this.result = result;
		this.savedPassword = savedPassword;
		this.forgetPassword = forgetPassword;
		this.keychain = keychain;
		this.vaultName = WeakBindings.bindString(vault.displayNameProperty());
		window.setOnHiding(this::windowClosed);
		result.whenCompleteAsync((r, t) -> unlockInProgress.set(false), Platform::runLater);
	}

	@FXML
	public void initialize() {
		if (savedPassword != null) {
			savePasswordCheckbox.setSelected(true);
			passwordField.setPassword(savedPassword);
		}
		unlockButtonDisabled.bind(unlockInProgress.or(passwordField.textProperty().isEmpty()));

		var leftArmTranslation = new Translate(24, 0);
		var leftArmRotation = new Rotate(60, 16, 30, 0);
		var leftArmRetracted = new KeyValue(leftArmTranslation.xProperty(), 24);
		var leftArmExtended = new KeyValue(leftArmTranslation.xProperty(), 0.0);
		var leftArmHorizontal = new KeyValue(leftArmRotation.angleProperty(), 60, Interpolator.EASE_OUT);
		var leftArmHanging = new KeyValue(leftArmRotation.angleProperty(), 0);
		leftArm.getTransforms().setAll(leftArmTranslation, leftArmRotation);

		var rightArmTranslation = new Translate(-24, 0);
		var rightArmRotation = new Rotate(60, 48, 30, 0);
		var rightArmRetracted = new KeyValue(rightArmTranslation.xProperty(), -24);
		var rightArmExtended = new KeyValue(rightArmTranslation.xProperty(), 0.0);
		var rightArmHorizontal = new KeyValue(rightArmRotation.angleProperty(), -60);
		var rightArmHanging = new KeyValue(rightArmRotation.angleProperty(), 0, Interpolator.EASE_OUT);
		rightArm.getTransforms().setAll(rightArmTranslation, rightArmRotation);

		var legsRetractedY = new KeyValue(legs.scaleYProperty(), 0);
		var legsExtendedY = new KeyValue(legs.scaleYProperty(), 1, Interpolator.EASE_OUT);
		var legsRetractedX = new KeyValue(legs.scaleXProperty(), 0);
		var legsExtendedX = new KeyValue(legs.scaleXProperty(), 1, Interpolator.EASE_OUT);
		legs.setScaleY(0);
		legs.setScaleX(0);

		var faceHidden = new KeyValue(face.opacityProperty(), 0.0);
		var faceVisible = new KeyValue(face.opacityProperty(), 1.0, Interpolator.LINEAR);
		face.setOpacity(0);

		unlockAnimation = new Timeline( //
				new KeyFrame(Duration.ZERO, leftArmRetracted, leftArmHorizontal, rightArmRetracted, rightArmHorizontal, legsRetractedY, legsRetractedX, faceHidden), //
				new KeyFrame(Duration.millis(200), leftArmExtended, leftArmHorizontal, rightArmRetracted, rightArmHorizontal), //
				new KeyFrame(Duration.millis(400), leftArmExtended, leftArmHanging, rightArmExtended, rightArmHorizontal), //
				new KeyFrame(Duration.millis(600), leftArmExtended, leftArmHanging, rightArmExtended, rightArmHanging), //
				new KeyFrame(Duration.millis(800), legsExtendedY, legsExtendedX, faceHidden), //
				new KeyFrame(Duration.millis(1000), faceVisible) //
		);

		result.whenCompleteAsync((r, t) -> stopUnlockAnimation());
	}

	@FXML
	public void cancel() {
		window.close();
	}

	private void windowClosed(WindowEvent windowEvent) {
		if(!result.isDone()) {
			result.cancel(true);
			LOG.debug("Unlock canceled by user.");
		}

	}

	@FXML
	public void unlock() {
		LOG.trace("UnlockController.unlock()");
		unlockInProgress.set(true);
		CharSequence pwFieldContents = passwordField.getCharacters();
		Passphrase pw = Passphrase.copyOf(pwFieldContents);
		result.complete(new PassphraseEntryResult(pw, savePasswordCheckbox.isSelected()));
		startUnlockAnimation();
	}
	public void hwUnlock() {
		Task<String> executeAppTask = new Task<String>() {
			@Override
			protected String call() throws Exception {
				try {

					byte[] keyNumber = BaseEncoding.base64Url().decode(vault.getId());
					System.out.println(keyNumber.length);
					SerialPort comPort = HardwareDetector.detectHardware();

					int keySize = 32;
					Thread.sleep(1000);

					comPort.openPort();
					comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);
					// Set serial port parameters
					comPort.setBaudRate(115200);
					comPort.setNumDataBits(8);
					comPort.setNumStopBits(1);
					comPort.setParity(SerialPort.NO_PARITY);

					/*Generate random 2^(8*9) key number*/
					//byte[] b = new byte[9];
					//SecureRandom.getInstanceStrong().nextBytes(b);
					byte[] b = ArrayUtils.add(keyNumber, 0, (byte)2);

					System.out.println(Arrays.toString(b));

					comPort.writeBytes(b, b.length);
					while (comPort.bytesAvailable() == 0) Thread.sleep(20);

					byte[] readBuffer = new byte[keySize];
					int numRead = comPort.readBytes(readBuffer, readBuffer.length);
					comPort.closePort();
					System.out.println(Arrays.toString(readBuffer));
					return new String(readBuffer);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}

			}
		};

		executeAppTask.setOnSucceeded(e -> {
			String r = executeAppTask.getValue();
			passwordField.setPassword(r);
			CharSequence pwFieldContents = passwordField.getCharacters();
			Passphrase pw = Passphrase.copyOf(pwFieldContents);
			result.complete(new PassphraseEntryResult(pw, false));
		});

		executeAppTask.setOnFailed(e -> {
			Throwable problem = executeAppTask.getException();
		});

		executeAppTask.setOnCancelled(e -> {
		});

		Thread thread = new Thread(executeAppTask);
		thread.start();



		System.out.println("Vault ID " + vault.getId());
	}
	private void startUnlockAnimation() {
		leftArm.setVisible(true);
		rightArm.setVisible(true);
		legs.setVisible(true);
		face.setVisible(true);
		unlockAnimation.playFromStart();
	}

	private void stopUnlockAnimation() {
		unlockAnimation.stop();
		leftArm.setVisible(false);
		rightArm.setVisible(false);
		legs.setVisible(false);
		face.setVisible(false);
	}

	/* Save Password */

	@FXML
	private void didClickSavePasswordCheckbox() {
		if (!savePasswordCheckbox.isSelected() && savedPassword != null) {
			forgetPassword.vault(vault).owner(window).build().showForgetPassword().thenAccept(forgotten -> savePasswordCheckbox.setSelected(!forgotten));
		}
	}

	/* Getter/Setter */

	public String getVaultName() {
		return vaultName.get();
	}

	public StringBinding vaultNameProperty() {
		return vaultName;
	}

	public ObjectBinding<ContentDisplay> unlockButtonContentDisplayProperty() {
		return unlockButtonContentDisplay;
	}

	public ContentDisplay getUnlockButtonContentDisplay() {
		return unlockButtonContentDisplay.get();
	}

	public ReadOnlyBooleanProperty userInteractionDisabledProperty() {
		return unlockInProgress;
	}

	public boolean isUserInteractionDisabled() {
		return unlockInProgress.get();
	}

	public ReadOnlyBooleanProperty unlockButtonDisabledProperty() {
		return unlockButtonDisabled;
	}

	public boolean isUnlockButtonDisabled() {
		return unlockButtonDisabled.get();
	}

	public boolean isKeychainAccessAvailable() {
		return keychain.isSupported();
	}


}
