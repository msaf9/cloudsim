package coms.project.enhanced;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
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
 * Enhanced CloudSim Round-Robin simulation with security and privacy measures.
 * 
 * This class sets up the CloudSim environment, creates datacenters, VMs, and cloudlets,
 * simulates various attacks, enforces access control, runs a time-shared scheduler,
 * and performs security and privacy monitoring on the results.
 * 
 */
public class CloudSimRREnhanced {
    /**
     * The broker managing VM and cloudlet submissions.
     */
    public static DatacenterBroker broker;

    /**
     * List of cloudlets to be executed.
     */
    private static List<Cloudlet> cloudletList;

    /**
     * List of virtual machines to host cloudlets.
     */
    private static List<Vm> vmlist;

    /**
     * Access control manager for users.
     */
    private static AccessControl acl;

    /**
     * Maximum number of cloudlets allowed per submission.
     */
    private static final int MAX_CLOUDLETS = 50;

    /**
     * Main entry point to run the enhanced CloudSim simulation.
     * 
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimRREnhanced...");
        try {
            // Init ACL and Crypto
            acl = new AccessControl();
            CryptoUtils.init();

            // Init CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create Datacenters
            Datacenter dc0 = createDatacenter("Datacenter_0");
            Datacenter dc1 = createDatacenter("Datacenter_1");

            // Create Broker and register role
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();
            acl.registerUser(brokerId, "BROKER");

            // Create VMs and Cloudlets
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // Encrypt VM metadata
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

            // Controlled submission
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

            // ========================
            // Round-Robin Scheduling
            // (Implicit via CloudletSchedulerTimeShared)
            // ========================
            // No additional mapping: time-shared scheduler applies RR

            // Start simulation
            CloudSim.startSimulation();
            List<Cloudlet> results = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // Monitoring & privacy
            double avg = SecurityMonitor.calculateAverageExecTime(results);
            double threshold = avg * 3;
            SecurityMonitor.checkExecutionAnomalies(results, threshold);
            SecurityMonitor.checkWorkloadDistribution(results, threshold);
            SecurityMonitor.checkPrivacyLeaksEncrypted(results);

            // Membership-inference attack
            AttackSimulator.simulateInferenceAttack(results);

            // Privacy analysis
            PrivacyMonitor.checkIOLarge(results);
            PrivacyMonitor.applyDifferentialPrivacy(0.2);
            PrivacyMonitor.applyDifferentialPrivacy(0.5);

            // Print results
            printCloudletList(results);
            Log.println("Simulation completed with enhanced security & privacy");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Simulation terminated: " + e.getMessage());
        }
    }

    /**
     * Creates a list of virtual machines for a given user.
     * 
     * @param userId the ID of the user submitting the VMs
     * @param vms the number of VMs to create
     * @return list of configured VMs
     */
    private static List<Vm> createVM(int userId, int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000; // image size (MB)
        int ram = 512;     // VM memory (MB)
        int mips = 1000;   // processing capacity
        long bw = 1000;    // bandwidth
        for (int i = 0; i < vms; i++) {
            list.add(new Vm(i, userId, mips, 1, ram, bw, size, "Xen", new CloudletSchedulerTimeShared()));
        }
        return list;
    }

    /**
     * Creates a list of cloudlets for a given user.
     * 
     * @param userId the ID of the user submitting the cloudlets
     * @param cloudlets the number of cloudlets to create
     * @return list of configured cloudlets
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        long fileSize = 300;   // input file size (MB)
        long outputSize = 300; // output file size (MB)
        int pesNumber = 1;     // number of CPUs
        UtilizationModel um = new UtilizationModelFull();
        long minLen = 500;     // minimum length of cloudlet
        long maxLen = 2000;    // maximum length of cloudlet
        Random rand = new Random(12345);
        for (int i = 0; i < cloudlets; i++) {
            long length = minLen + rand.nextInt((int) (maxLen - minLen + 1));
            Cloudlet cl = new Cloudlet(i, length, pesNumber, fileSize, outputSize, um, um, um);
            cl.setUserId(userId);
            list.add(cl);
        }
        return list;
    }

    /**
     * Creates a datacenter with a predefined set of hosts.
     * 
     * @param name the name of the datacenter
     * @return configured Datacenter instance
     * @throws Exception if datacenter creation fails
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();
        int mips = 1000; // processing capacity for each PE

        // Host 0 with 4 PEs
        List<Pe> p1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            p1.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        // Host 1 with 2 PEs
        List<Pe> p2 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            p2.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        hostList.add(new Host(0, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000),
                1000000, p1, new VmSchedulerTimeShared(p1)));
        hostList.add(new Host(1, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000),
                1000000, p2, new VmSchedulerTimeShared(p2)));

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
                "x86",    // architecture
                "Linux", // OS
                "Xen",   // virtualization
                hostList,
                0,        // time zone
                3.0,      // cost per second
                0.05,     // cost per memory unit
                0.1,      // cost per storage unit
                0.1       // cost per bandwidth unit
        );

        return new Datacenter(name, chars, new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(), 0);
    }

    /**
     * Prints a formatted summary of cloudlet execution results to the log.
     * 
     * @param list list of completed Cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) {
        String[] headers = { "CloudletID", "STATUS", "DCID", "VMID", "ExecTime", "StartTime", "FinishTime",
                "WaitTime" };
        List<String[]> rows = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("###.##");
        int cols = headers.length;
        int[] w = new int[cols];
        for (int i = 0; i < cols; i++) {
            w[i] = headers[i].length();
        }
        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                double exec = c.getActualCPUTime();
                double start = c.getExecStartTime();
                double finish = c.getExecFinishTime();
                double wait = finish - start - exec;
                String[] row = {
                        String.valueOf(c.getCloudletId()),
                        "SUCCESS",
                        String.valueOf(c.getResourceId()),
                        String.valueOf(c.getGuestId()),
                        df.format(exec),
                        df.format(start),
                        df.format(finish),
                        df.format(wait)
                };
                rows.add(row);
                for (int i = 0; i < cols; i++) {
                    w[i] = Math.max(w[i], row[i].length());
                }
            }
        }
        StringBuilder fmt = new StringBuilder();
        for (int width : w) {
            fmt.append(" %-" + width + "s");
        }
        Log.println(String.format(fmt.toString(), (Object[]) headers));
        for (String[] row : rows) {
            Log.println(String.format(fmt.toString(), (Object[]) row));
        }
    }
}
