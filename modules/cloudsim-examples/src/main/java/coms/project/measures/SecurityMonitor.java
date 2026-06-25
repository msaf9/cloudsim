package coms.project.measures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;

import coms.project.attack.CryptoUtils;

/**
 * SecurityMonitor provides runtime security checks on cloud workload behavior:
 * - Detects execution anomalies, uneven workload distribution, and potential privacy leaks.
 * - Logs alerts in both encrypted and decrypted form using CryptoUtils.
 */
public class SecurityMonitor {

	/**
	 * Calculates the average execution time (CPU time) across all cloudlets.
	 *
	 * @param list List of cloudlets
	 * @return Average actual CPU time
	 */
	public static double calculateAverageExecTime(List<Cloudlet> list) {
		return list.stream().mapToDouble(Cloudlet::getActualCPUTime).average().orElse(0);
	}

	/**
	 * Detects execution anomalies:
	 * - Triggers alert for any cloudlet exceeding the provided time threshold.
	 *
	 * @param list List of cloudlets
	 * @param thr  Execution time threshold
	 */
	public static void checkExecutionAnomalies(List<Cloudlet> list, double thr) {
		for (Cloudlet c : list) {
			if (c.getActualCPUTime() > thr) {
				try {
					String alert = "Exec anomaly: Cloudlet " + c.getCloudletId();
					String ct = CryptoUtils.encrypt(alert);
					Log.println("ENCRYPTED_ALERT: " + ct);
					Log.println("DECRYPTED_ALERT: " + alert);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Checks for uneven workload distribution among VMs:
	 * - Compares number of tasks assigned to each VM.
	 * - Triggers alert if deviation from average exceeds 50%.
	 *
	 * @param list List of cloudlets
	 * @param thr  (Currently unused) Threshold parameter placeholder
	 */
	public static void checkWorkloadDistribution(List<Cloudlet> list, double thr) {
		Map<Integer, Integer> counts = new HashMap<>();
		for (Cloudlet c : list) {
			counts.merge(c.getGuestId(), 1, Integer::sum);
		}
		double avg = list.size() / (double) counts.size();
		for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
			if (Math.abs(e.getValue() - avg) > avg * 0.5) {
				try {
					String alert = "Uneven workload: VM " + e.getKey() + " has " + e.getValue();
					String ct = CryptoUtils.encrypt(alert);
					Log.println("ENCRYPTED_ALERT: " + ct);
					Log.println("DECRYPTED_ALERT: " + alert);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	/**
	 * Detects potential privacy leaks based on cloudlet I/O size:
	 * - Triggers alert if file or output size exceeds 500 units.
	 *
	 * @param list List of cloudlets
	 */
	public static void checkPrivacyLeaksEncrypted(List<Cloudlet> list) {
		for (Cloudlet c : list) {
			if (c.getCloudletFileSize() > 500 || c.getCloudletOutputSize() > 500) {
				try {
					String alert = "Privacy leak: Cloudlet " + c.getCloudletId();
					String ct = CryptoUtils.encrypt(alert);
					Log.println("ENCRYPTED_ALERT: " + ct);
					Log.println("DECRYPTED_ALERT: " + alert);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
