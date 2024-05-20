package org.cryptomator.ui.changepassword;

import org.cryptomator.common.Passphrase;
import org.cryptomator.ui.common.FxController;
import org.cryptomator.ui.controls.FontAwesome5IconView;
import org.cryptomator.ui.controls.FontAwesome5Spinner;
import org.cryptomator.ui.controls.NiceSecurePasswordField;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.ResourceBundle;

import com.fazecast.jSerialComm.SerialPort;

public class NewPasswordController implements FxController {

	private final ResourceBundle resourceBundle;
	private final PasswordStrengthUtil strengthRater;
	private final IntegerProperty passwordStrength = new SimpleIntegerProperty(-1);
	private final BooleanProperty goodPassword = new SimpleBooleanProperty();
	private BooleanProperty hardware = new SimpleBooleanProperty(false);
	public NiceSecurePasswordField passwordField;
	public NiceSecurePasswordField reenterField;
	public Label passwordStrengthLabel;
	public FontAwesome5IconView passwordStrengthCheckmark;
	public FontAwesome5IconView passwordStrengthWarning;
	public FontAwesome5IconView passwordStrengthCross;
	public Label passwordMatchLabel;
	public FontAwesome5IconView passwordMatchCheckmark;
	public FontAwesome5IconView passwordMatchCross;
	public Button passwordHardwareBtn;
	public FontAwesome5Spinner spinner;

	public byte[] randomKeyNumber;

	public NewPasswordController(ResourceBundle resourceBundle, PasswordStrengthUtil strengthRater) {
		this.resourceBundle = resourceBundle;
		this.strengthRater = strengthRater;
	}

	@FXML
	public void initialize() {
		passwordStrength.bind(Bindings.createIntegerBinding(() -> strengthRater.computeRate(passwordField.getCharacters()), passwordField.textProperty()));

		passwordStrengthLabel.graphicProperty().bind(Bindings.createObjectBinding(this::getIconViewForPasswordStrengthLabel, passwordField.textProperty(), passwordStrength));
		passwordStrengthLabel.textProperty().bind(passwordStrength.map(strengthRater::getStrengthDescription));

		BooleanBinding passwordsMatch = Bindings.createBooleanBinding(this::passwordFieldsMatch, passwordField.textProperty(), reenterField.textProperty());
		BooleanBinding reenterFieldNotEmpty = reenterField.textProperty().isNotEmpty();
		passwordMatchLabel.visibleProperty().bind(reenterFieldNotEmpty);
		passwordMatchLabel.graphicProperty().bind(Bindings.when(passwordsMatch.and(reenterFieldNotEmpty)).then(passwordMatchCheckmark).otherwise(passwordMatchCross));
		passwordMatchLabel.textProperty().bind(Bindings.when(passwordsMatch.and(reenterFieldNotEmpty)).then(resourceBundle.getString("newPassword.passwordsMatch")).otherwise(resourceBundle.getString("newPassword.passwordsDoNotMatch")));

		BooleanBinding sufficientStrength = Bindings.createBooleanBinding(this::sufficientStrength, passwordField.textProperty());
		goodPassword.bind(passwordsMatch.and(sufficientStrength));
	}

	private FontAwesome5IconView getIconViewForPasswordStrengthLabel() {
		if (passwordField.getCharacters().length() == 0) {
			return null;
		} else if (passwordStrength.intValue() <= -1) {
			return passwordStrengthCross;
		} else if (passwordStrength.intValue() < 3) {
			return passwordStrengthWarning;
		} else {
			return passwordStrengthCheckmark;
		}
	}

	private boolean passwordFieldsMatch() {
		return CharSequence.compare(passwordField.getCharacters(), reenterField.getCharacters()) == 0;
	}

	private boolean sufficientStrength() {
		return strengthRater.fulfillsMinimumRequirements(passwordField.getCharacters());
	}

	/* Getter/Setter */

	public ReadOnlyBooleanProperty goodPasswordProperty() {
		return goodPassword;
	}

	public boolean isGoodPassword() {
		return goodPassword.get();
	}

	public IntegerProperty passwordStrengthProperty() {
		return passwordStrength;
	}

	public int getPasswordStrength() {
		return passwordStrength.get();
	}

	public Passphrase getNewPassword() {
		return passwordField.getCharacters();
	}

	public void getHardwarePassword(ActionEvent actionEvent) {
		class HwResult {
			private String password;
			private byte[] randomKeyNumber;

			public HwResult(String password, byte[] randomKeyNumber) {
				this.password = password;
				this.randomKeyNumber = randomKeyNumber;
			}

			public String getPassword() {
				return password;
			}
			public byte[] getRandomKeyNumber() {
				return randomKeyNumber;
			}
		}
		Task<HwResult> executeAppTask = new Task<HwResult>() {
			@Override
			protected HwResult call() throws Exception {
				try {
					spinner.setVisible(true);
					SerialPort comPort = SerialPort.getCommPorts()[0];

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
					byte[] b = new byte[9];
					SecureRandom.getInstanceStrong().nextBytes(b);
					b[0] = 2;
					System.out.println(Arrays.toString(b));

					comPort.writeBytes(b, b.length);
					while (comPort.bytesAvailable() == 0) Thread.sleep(20);

					byte[] readBuffer = new byte[keySize];
					int numRead = comPort.readBytes(readBuffer, readBuffer.length);
					comPort.closePort();
					System.out.println(Arrays.toString(readBuffer));
					return new HwResult(new String(readBuffer), Arrays.copyOfRange(b, 1, b.length));



				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}

			}
		};

		executeAppTask.setOnSucceeded(e -> {
			HwResult result = executeAppTask.getValue();
			passwordField.setPassword(result.getPassword());
			reenterField.setPassword(result.getPassword());

			randomKeyNumber = result.getRandomKeyNumber();
			hardware.setValue(true);
			spinner.setVisible(false);
		});

		executeAppTask.setOnFailed(e -> {
			Throwable problem = executeAppTask.getException();
		});

		executeAppTask.setOnCancelled(e -> {
		});

		Thread thread = new Thread(executeAppTask);
		thread.start();
	}

	public boolean isHardware() {
		return hardware.get();
	}
}
