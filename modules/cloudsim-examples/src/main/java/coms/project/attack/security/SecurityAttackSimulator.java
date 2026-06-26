package coms.project.attack.security;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;

/**
 * SecurityAttackSimulator simulates attacks aimed at resource disruption and integrity:
 * - Denial of Service (DoS): Exhausting broker/scheduler resources.
 * - Data Poisoning: Manipulating workloads to skew scheduler logic.
 */
public class SecurityAttackSimulator {

    /**
     * Simulates a Denial-of-Service (DoS) attack:
     * - Creates 'n' small cloudlets.
     * - Submits them all at once to overload the broker.
     * - Logs attack size.
     */
    public static void simulateDoS(DatacenterBroker broker, int n) {
        List<Cloudlet> dos = new ArrayList<>();
        UtilizationModelFull um = new UtilizationModelFull();
        for (int i = 1000; i < 1000 + n; i++) {
            Cloudlet cl = new Cloudlet(i, 10, 1, 10, 10, um, um, um);
            cl.setUserId(broker.getId());
            dos.add(cl);
        }
        broker.submitCloudletList(dos);
        Log.println("[SecurityAttackSimulator] DoS attack: " + n + " cloudlets");
    }

    /**
     * Simulates data poisoning:
     * - Selects half of the cloudlets.
     * - Multiplies their workload length by 10.
     * - Mimics training-time poisoning in ML or resource overloads.
     */
    public static void simulatePoisoning(List<Cloudlet> list) {
        for (int i = 0; i < list.size() / 2; i++) {
            list.get(i).setCloudletLength(list.get(i).getCloudletLength() * 10);
        }
        Log.println("[SecurityAttackSimulator] Poisoning applied on " + (list.size() / 2) + " cloudlets");
    }
}
