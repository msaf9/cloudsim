package coms.project.attack.privacy;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;


/**
 * PrivacyAttackSimulator simulates attacks aimed at data exfiltration and side-channels:
 * - Metadata Leakage: Extracting underlying VM configurations.
 * - Timing Analysis: Observing scheduler latency.
 * - Inference Attacks: Deducing sensitivity based on execution patterns.
 */
public class PrivacyAttackSimulator {

    /**
     * Encrypts VM-local metadata:
     * - Formats VM ID, MIPS, and RAM into a string.
     * - Encrypts it using CryptoUtils and stores in VM's VMM field.
     * - Mimics a metadata obfuscation scenario.
     */
    public static void encryptVMLocalMetadata(List<Vm> vms) {
        try {
            for (Vm vm : vms) {
                String info = "VM" + vm.getId() + ":" + vm.getMips() + "," + vm.getRam();
                vm.setVmm(CryptoUtils.encrypt(info));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Simulates encrypted data leakage:
     * - Decrypts VM metadata from VMM field.
     * - Appends it to a "leak" message, encrypts the result.
     * - Logs both encrypted and decrypted forms.
     */
    public static void simulateDataLeakageEncrypted(List<Vm> vms) {
        for (Vm vm : vms) {
            try {
                String metaPT = CryptoUtils.decrypt(vm.getVmm());
                String leakMsg = "VM" + vm.getId() + " leak:" + metaPT;
                String ct = CryptoUtils.encrypt(leakMsg);
                Log.println("ENCRYPTED_LEAK: " + ct);
                Log.println("DECRYPTED_LEAK: " + leakMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Simulates timing analysis attack:
     * - Measures execution time for iterating over cloudlet list.
     * - Illustrates side-channel observation via time deltas.
     */
    public static void simulateTimingAnalysis(DatacenterBroker broker) {
        long t1 = System.currentTimeMillis();
        broker.getCloudletList().forEach(cl -> {
            // Potential target: simulate subtle measurements.
        });
        long t2 = System.currentTimeMillis();
        Log.println("[PrivacyAttackSimulator] Timing analysis took " + (t2 - t1) + " ms");
    }

    /**
     * Membership‐inference attack:
     * - IDs [0..N/2) are “sensitive.”
     * - Learns threshold = avg CPU time of sensitive set.
     * - Guesses based on that threshold.
     * - Logs encrypted via CloudSim and prints to System.out.
     */
    public static void simulateInferenceAttack(List<Cloudlet> results) {
        int half = results.size() / 2;
        List<Cloudlet> sens = new ArrayList<>();
        for (Cloudlet c : results)
            if (c.getCloudletId() < half)
                sens.add(c);

        double threshold = sens.stream().mapToDouble(Cloudlet::getActualCPUTime).average().orElse(0);

        int correct = 0, total = results.size();
        for (Cloudlet c : results) {
            boolean trueLabel = c.getCloudletId() < half;
            boolean guess = c.getActualCPUTime() > threshold;
            if (guess == trueLabel)
                correct++;
        }

        String report = String.format("Membership inference accuracy: %.2f%% (threshold=%.2f)",
                correct / (double) total * 100, threshold);

        try {
            String ct = CryptoUtils.encrypt(report);
            Log.println("ENCRYPTED_PRIVACY_ATTACK: " + ct);
            Log.println("DECRYPTED_PRIVACY_ATTACK: " + report);

            System.out.println(">>> PRIVACY_ATTACK_RAW: " + report);
            System.out.println(">>> PRIVACY_ATTACK_ENC: " + ct);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
