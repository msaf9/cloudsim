package coms.project.enhanced;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import coms.project.attack.AttackSimulator;
import coms.project.attack.CryptoUtils;
import coms.project.measures.AccessControl;
import coms.project.measures.PrivacyMonitor;
import coms.project.measures.SecurityMonitor;

/**
 * Enhanced CloudSim FCFS simulator.
 * 
 * Provides additional security and privacy features including:
 * 
 * - AES encryption for logs and metadata</li>
 * - Role-based access control (RBAC) with submission quotas</li>
 * - Dynamic anomaly detection thresholds</li>
 * - Attack simulations and monitoring</li>
 * - Privacy analysis tools (I/O detection, differential privacy, inference attacks)</li>
 *
 */
public class CloudSimFCFSEnhanced {

    /** Broker for cloudlet and VM submissions. */
    public static DatacenterBroker broker;

    /** List of cloudlets to be submitted to the datacenter. */
    private static List<Cloudlet> cloudletList;

    /** List of virtual machines to be created in the datacenter. */
    private static List<Vm> vmlist;

    /** Access control manager instance. */
    private static AccessControl acl;

    /** Maximum allowed number of cloudlets for submission. */
    private static final int MAX_CLOUDLETS = 50;

    /**
     * Entry point for the enhanced CloudSim FCFS simulation.
     * 
     * Initializes security components, creates datacenters, VMs, and cloudlets,
     * simulates attacks, enforces policies, runs the CloudSim engine, collects
     * results, and performs security and privacy monitoring.
     *
     * @param args Command line arguments (unused)
     */
    public static void main(String[] args) {
        System.out.println("Starting CloudSimFCFS...");
        Log.println("Starting CloudSimFCFS with Enhanced Security & Privacy...");
        try {
            // Init ACL and crypto
            acl = new AccessControl();
            CryptoUtils.init();

            // Init CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create datacenters
            Datacenter dc0 = createDatacenter("Datacenter_0");
            Datacenter dc1 = createDatacenter("Datacenter_1");

            // Create broker and register role
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();
            acl.registerUser(brokerId, "BROKER");

            // Create resources
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // Encrypt VM metadata on creation
            AttackSimulator.encryptVMLocalMetadata(vmlist);

            // Simulate attacks
            AttackSimulator.simulateDoS(broker, 30);
            AttackSimulator.simulatePoisoning(cloudletList);
            AttackSimulator.simulateDataLeakageEncrypted(vmlist);
            AttackSimulator.simulateTimingAnalysis(broker);

            // Enforce submission quotas
            if (cloudletList.size() > MAX_CLOUDLETS) {
                throw new SecurityException("Submission quota exceeded: " + cloudletList.size());
            }

            // Controlled submission via ACL
            if (acl.isAllowed(brokerId, "SUBMIT")) {
                broker.submitGuestList(vmlist);
                broker.submitCloudletList(cloudletList);
            } else {
                String msg = "ACL_DENIAL: user " + brokerId + " denied submit";
                String ct = CryptoUtils.encrypt(msg);
                Log.println("ENCRYPTED_DENIAL: " + ct);
                Log.println("DECRYPTED_DENIAL: " + CryptoUtils.decrypt(ct));
                return;
            }

            // Run simulation
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // Gather results
            List<Cloudlet> results = broker.getCloudletReceivedList();

            // Perform security monitoring with dynamic threshold
            double avg = SecurityMonitor.calculateAverageExecTime(results);
            double threshold = avg * 3;
            SecurityMonitor.checkExecutionAnomalies(results, threshold);
            SecurityMonitor.checkWorkloadDistribution(results, threshold);
            SecurityMonitor.checkPrivacyLeaksEncrypted(results);

            // Membership-inference privacy attack simulation
            AttackSimulator.simulateInferenceAttack(results);

            // Privacy analysis measures
            PrivacyMonitor.checkIOLarge(results);
            PrivacyMonitor.applyDifferentialPrivacy(0.2);
            PrivacyMonitor.applyDifferentialPrivacy(0.5);

            // Print results to log
            printCloudletList(results);
            Log.println("Simulation completed with enhanced security & privacy");
            System.out.println(">>> Finished CloudSimFCFS"); // debug marker

        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Simulation terminated: " + e.getMessage());
        }
    }

    /**
     * Creates a list of virtual machines for a given user.
     *
     * @param userId ID of the user (broker) that owns the VMs
     * @param count Number of VMs to create
     * @return List of configured virtual machines
     */
    private static List<Vm> createVM(int userId, int count) {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vm vm = new Vm(i, userId, 1000, 1, 512, 1000, 10000, "Xen", new CloudletSchedulerSpaceShared());
            list.add(vm);
        }
        return list;
    }

    /**
     * Creates a list of cloudlets (tasks) for a given user.
     *
     * @param userId ID of the user (broker) that submits the cloudlets
     * @param count Number of cloudlets to create
     * @return List of configured cloudlets
     */
    private static List<Cloudlet> createCloudlet(int userId, int count) {
        List<Cloudlet> list = new ArrayList<>();
        UtilizationModelFull um = new UtilizationModelFull();
        for (int i = 0; i < count; i++) {
            Cloudlet cl = new Cloudlet(i, 1000, 1, 300, 300, um, um, um);
            cl.setUserId(userId);
            list.add(cl);
        }
        return list;
    }

    /**
     * Constructs a datacenter with a simple VM allocation policy.
     *
     * @param name Name identifier for the datacenter
     * @return Configured Datacenter instance
     * @throws Exception If creation of hosts or characteristics fails
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hosts = new ArrayList<>();
        List<Pe> pes1 = new ArrayList<>();
        List<Pe> pes2 = new ArrayList<>();
        // Two hosts with different core counts
        for (int i = 0; i < 4; i++) {
            pes1.add(new Pe(i, new PeProvisionerSimple(1000)));
        }
        for (int i = 0; i < 2; i++) {
            pes2.add(new Pe(i, new PeProvisionerSimple(1000)));
        }
        hosts.add(new Host(0, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000), 1000000, pes1,
                new VmSchedulerTimeShared(pes1)));
        hosts.add(new Host(1, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000), 1000000, pes2,
                new VmSchedulerTimeShared(pes2)));
        DatacenterCharacteristics dcChars = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hosts,
                0, 3.0, 0.05, 0.1, 0.1);
        return new Datacenter(name, dcChars, new VmAllocationPolicySimple(hosts), new LinkedList<Storage>(), 0);
    }

    /**
     * Logs the results of cloudlet executions.
     *
     * @param list List of executed cloudlets to print
     */
    private static void printCloudletList(List<Cloudlet> list) {
        DecimalFormat df = new DecimalFormat("###.##");
        String fmt = "%-5s %-10s %-7s %-7s %-7s %-7s %-7s";
        Log.println();
        Log.println(String.format(fmt, "ID", "Status", "DC", "VM", "Time", "Start", "Finish"));
        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.println(String.format(fmt,
                        c.getCloudletId(), "SUCCESS", c.getResourceId(), c.getGuestId(),
                        df.format(c.getActualCPUTime()), df.format(c.getExecStartTime()),
                        df.format(c.getExecFinishTime())));
            }
        }
    }
}
