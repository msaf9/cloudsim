package coms.project.monitor;

import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;

import coms.project.attack.CryptoUtils;

/**
 * PrivacyMonitor tracks potential privacy violations in cloud simulations:
 * - Monitors for abnormal I/O patterns and differential privacy budget overuse.
 * - Logs alerts in both encrypted and plaintext form using CryptoUtils.
 */
public class PrivacyMonitor {
	private static final long MAX_IO_SIZE = 500;
	private static final double MAX_DP_BUDGET = 1.0;
	private static double cumulativeEpsilon = 0.0;

	/**
	 * Checks for cloudlets with unusually large I/O size:
	 * - Triggers alert if combined input + output size exceeds MAX_IO_SIZE.
	 *
	 * @param list List of cloudlets to inspect
	 */
	public static void checkIOLarge(List<Cloudlet> list) {
		for (Cloudlet c : list) {
			long ioSize = c.getCloudletFileSize() + c.getCloudletOutputSize();
			if (ioSize > MAX_IO_SIZE) {
				alert("Large I/O leak", c.getCloudletId());
			}
		}
	}

	/**
	 * Applies differential privacy budget check:
	 * - Adds ε to cumulative budget.
	 * - Triggers alert if cumulative ε exceeds MAX_DP_BUDGET.
	 *
	 * @param epsilon Privacy budget increment (ε)
	 */
	public static void applyDifferentialPrivacy(double epsilon) {
		cumulativeEpsilon += epsilon;
		if (cumulativeEpsilon > MAX_DP_BUDGET) {
			alertSimple("DP budget exceeded: ε=" + cumulativeEpsilon);
		}
	}

	/**
	 * Logs a detailed alert message (includes cloudlet ID):
	 * - Encrypts message using CryptoUtils.
	 * - Logs both encrypted and decrypted forms.
	 *
	 * @param reason Reason for the alert
	 * @param id     Cloudlet ID
	 */
	private static void alert(String reason, int id) {
		try {
			String txt = reason + " in Cloudlet " + id;
			String ct = CryptoUtils.encrypt(txt);
			Log.println("ENCRYPTED_PRIVACY_ALERT: " + ct);
			Log.println("DECRYPTED_PRIVACY_ALERT: " + txt);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Logs a general alert message without cloudlet ID:
	 * - Used for system-wide or budget-related violations.
	 *
	 * @param txt Message to log
	 */
	private static void alertSimple(String txt) {
		try {
			String ct = CryptoUtils.encrypt(txt);
			Log.println("ENCRYPTED_PRIVACY_ALERT: " + ct);
			Log.println("DECRYPTED_PRIVACY_ALERT: " + txt);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
