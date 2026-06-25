package coms.project.enhanced;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
 * Main class for the enhanced CloudSim simulation using Max-Min scheduling.
 * Integrates security, privacy, and attack simulations with resource management.
 */
public class CloudSimMaxMinEnhanced {
    /** Broker for managing VM and cloudlet submissions. */
    public static DatacenterBroker broker;

    /** List of cloudlets to be submitted. */
    private static List<Cloudlet> cloudletList;

    /** List of virtual machines to be used in the simulation. */
    private static List<Vm> vmlist;

    /** Access control module for enforcing user permissions. */
    private static AccessControl acl;

    /** Maximum allowed number of cloudlets per simulation. */
    private static final int MAX_CLOUDLETS = 50;

    /**
     * Entry point of the simulation.
     * Initializes security, CloudSim, resources, applies attacks and defense measures,
     * and executes the Max-Min scheduling algorithm.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimMaxMinEnhanced...");
        try {
            // Init ACL and Crypto
            acl = new AccessControl();
            CryptoUtils.init();

            // Init CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create datacenters
            Datacenter dc0 = createDatacenter("Datacenter_0");
            Datacenter dc1 = createDatacenter("Datacenter_1");

            // Create broker and register
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();
            acl.registerUser(brokerId, "BROKER");

            // Create resources
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // Encrypt VM metadata
            AttackSimulator.encryptVMLocalMetadata(vmlist);

            // Simulate attacks
            AttackSimulator.simulateDoS(broker, 30);
            AttackSimulator.simulatePoisoning(cloudletList);
            AttackSimulator.simulateDataLeakageEncrypted(vmlist);
            AttackSimulator.simulateTimingAnalysis(broker);

            // Enforce quotas
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
            // Max-Min Scheduling
            // ========================
            applyMaxMin(broker, cloudletList, vmlist);
            // ========================
            // END Max-Min
            // ========================

            // Run simulation
            CloudSim.startSimulation();
            List<Cloudlet> results = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // Monitoring & privacy
            double avg = SecurityMonitor.calculateAverageExecTime(results);
            double threshold = avg * 3;
            SecurityMonitor.checkExecutionAnomalies(results, threshold);
            SecurityMonitor.checkWorkloadDistribution(results, threshold);
            SecurityMonitor.checkPrivacyLeaksEncrypted(results);

            // Inference attack
            AttackSimulator.simulateInferenceAttack(results);

            // Privacy analysis
            PrivacyMonitor.checkIOLarge(results);
            PrivacyMonitor.applyDifferentialPrivacy(0.2);
            PrivacyMonitor.applyDifferentialPrivacy(0.5);

            printCloudletList(results);
            Log.println("Simulation completed with enhanced security & privacy");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Simulation terminated: " + e.getMessage());
        }
    }

    /**
     * Applies the Max-Min scheduling algorithm by binding the longest cloudlets
     * to the VM with the earliest completion time.
     *
     * @param broker    the DatacenterBroker to bind cloudlets to VMs
     * @param cloudlets list of cloudlets to schedule
     * @param vms       list of available virtual machines
     */
    private static void applyMaxMin(DatacenterBroker broker, List<Cloudlet> cloudlets, List<Vm> vms) {
        Map<Integer, Double> vmCompletion = new HashMap<>();
        for (Vm vm : vms) {
            vmCompletion.put(vm.getId(), 0.0);
        }
        List<Cloudlet> unsettled = new ArrayList<>(cloudlets);
        while (!unsettled.isEmpty()) {
            Cloudlet maxCl = Collections.max(unsettled, Comparator.comparingLong(Cloudlet::getCloudletLength));
            Vm target = Collections.min(vms, Comparator.comparing(vm -> vmCompletion.get(vm.getId())));
            double exec = (double) maxCl.getCloudletLength() / target.getMips();
            broker.bindCloudletToVm(maxCl.getCloudletId(), target.getId());
            vmCompletion.put(target.getId(), vmCompletion.get(target.getId()) + exec);
            unsettled.remove(maxCl);
        }
    }

    /**
     * Creates a list of VMs for the given user.
     *
     * @param userId ID of the user (broker) creating the VMs
     * @param vms    number of VMs to create
     * @return list of created VMs
     */
    private static List<Vm> createVM(int userId, int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000; // image size in MB
        int ram = 512;     // VM memory in MB
        int mips = 1000;   // processing power
        long bw = 1000;    // bandwidth
        for (int i = 0; i < vms; i++) {
            list.add(new Vm(i, userId, mips, 1, ram, bw, size, "Xen", new CloudletSchedulerTimeShared()));
        }
        return list;
    }

    /**
     * Creates a list of Cloudlets with random lengths for the given user.
     *
     * @param userId    ID of the user (broker) submitting the cloudlets
     * @param cloudlets number of cloudlets to create
     * @return list of created Cloudlets
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        Random rand = new Random(1234);
        long fileSize = 300;
        long outputSize = 300;
        UtilizationModel um = new UtilizationModelFull();
        for (int i = 0; i < cloudlets; i++) {
            long length = 500 + rand.nextInt(4501); // between 500 and 5000
            Cloudlet cl = new Cloudlet(i, length, 1, fileSize, outputSize, um, um, um);
            cl.setUserId(userId);
            list.add(cl);
        }
        return list;
    }

    /**
     * Creates a Datacenter with two hosts of varying CPU core counts.
     *
     * @param name name of the Datacenter
     * @return the created Datacenter instance
     * @throws Exception if datacenter creation fails
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hosts = new ArrayList<>();
        int mips = 1000;
        List<Pe> p1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            p1.add(new Pe(i, new PeProvisionerSimple(mips)));
        }
        List<Pe> p2 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            p2.add(new Pe(i, new PeProvisionerSimple(mips)));
        }
        hosts.add(new Host(0, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000), 1000000, p1,
                new VmSchedulerTimeShared(p1)));
        hosts.add(new Host(1, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000), 1000000, p2,
                new VmSchedulerTimeShared(p2)));
        DatacenterCharacteristics chars = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hosts, 0, 3.0, 0.05, 0.1, 0.1
        );
        return new Datacenter(name, chars, new VmAllocationPolicySimple(hosts), new LinkedList<Storage>(), 0);
    }

    /**
     * Prints the results of cloudlet execution in a formatted table.
     *
     * @param list list of executed cloudlets to print
     */
    private static void printCloudletList(List<Cloudlet> list) {
        String[] headers = { "Cloudlet ID", "STATUS", "DC ID", "VM ID", "Time", "Start", "Finish" };
        List<String[]> rows = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("###.##");
        int cols = headers.length;
        int[] w = new int[cols];
        for (int i = 0; i < cols; i++) {
            w[i] = headers[i].length();
        }
        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                String[] row = {
                    String.valueOf(c.getCloudletId()),
                    "SUCCESS",
                    String.valueOf(c.getResourceId()),
                    String.valueOf(c.getGuestId()),
                    df.format(c.getActualCPUTime()),
                    df.format(c.getExecStartTime()),
                    df.format(c.getExecFinishTime())
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
