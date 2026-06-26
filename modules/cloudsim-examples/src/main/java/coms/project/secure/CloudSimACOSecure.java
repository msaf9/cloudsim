package coms.project.secure;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
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

import coms.project.attack.CryptoUtils;
import coms.project.attack.privacy.PrivacyAttackSimulator;
import coms.project.attack.security.SecurityAttackSimulator;
import coms.project.monitor.AccessControl;
import coms.project.monitor.PrivacyMonitor;
import coms.project.monitor.SecurityMonitor;

/**
 * CloudSimACOEnhanced is an enhanced CloudSim simulation example integrating
 * ACO-based scheduling with security and privacy measures.
 * 
 * It initializes CloudSim, creates datacenters, VMs, and cloudlets, applies
 * attack simulations, enforces quotas and access control, runs ACO for task scheduling,
 * and performs monitoring and privacy analysis on the simulation results.
 * 
 */
public class CloudSimACOSecure {

    /**
     * The broker used to submit VMs and cloudlets.
     */
    public static DatacenterBroker broker;

    /**
     * List of cloudlets to be scheduled and executed.
     */
    private static List<Cloudlet> cloudletList;

    /**
     * List of virtual machines available for scheduling.
     */
    private static List<Vm> vmlist;

    /**
     * Access control manager for users and actions.
     */
    private static AccessControl acl;

    /**
     * Maximum allowed cloudlet submissions to prevent quota breach.
     */
    private static final int MAX_CLOUDLETS = 50;

    /**
     * Main entry point for the simulation.
     * Initializes security, CloudSim, resources, runs ACO scheduling,
     * and performs security & privacy monitoring.
     *
     * @param args command line arguments (unused)
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimACOEnhanced...");
        try {
            // Init ACL and Crypto
            acl = new AccessControl();
            CryptoUtils.init();

            // Init CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create datacenters
            Datacenter datacenter0 = createDatacenter("Datacenter_0");
            Datacenter datacenter1 = createDatacenter("Datacenter_1");

            // Create broker and register
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();
            acl.registerUser(brokerId, "BROKER");

            // Create resources
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // Encrypt VM metadata
//            AttackSimulator.encryptVMLocalMetadata(vmlist);

            // Simulate attacks
//            AttackSimulator.simulateDoS(broker, 30);
//            AttackSimulator.simulatePoisoning(cloudletList);
//            AttackSimulator.simulateDataLeakageEncrypted(vmlist);
//            AttackSimulator.simulateTimingAnalysis(broker);
            
            // Privacy Simulator calls
            PrivacyAttackSimulator.encryptVMLocalMetadata(vmlist);
            PrivacyAttackSimulator.simulateDataLeakageEncrypted(vmlist);
            PrivacyAttackSimulator.simulateTimingAnalysis(broker);

            // Security Simulator calls
            SecurityAttackSimulator.simulateDoS(broker, 30);
            SecurityAttackSimulator.simulatePoisoning(cloudletList);

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
            // ACO‐BASED SCHEDULING
            // ========================
            int numAnts = 30;
            int maxIter = 50;
            double alpha = 1.0, beta = 2.0, evap = 0.5, Q = 500.0;

            int C = cloudletList.size();
            int V = vmlist.size();

            double[][] pheromone = new double[C][V];
            double[][] heuristic = new double[C][V];

            // Init pheromone & heuristic
            for (int i = 0; i < C; i++) {
                long length = cloudletList.get(i).getCloudletLength();
                for (int j = 0; j < V; j++) {
                    pheromone[i][j] = 1.0;
                    heuristic[i][j] = 1.0 / (length / (double) vmlist.get(j).getMips());
                }
            }

            Random rand = new Random();
            int[] bestAssign = new int[C];
            double bestMakespan = Double.MAX_VALUE;

            for (int iter = 0; iter < maxIter; iter++) {
                List<int[]> solList = new ArrayList<>();
                List<Double> makespans = new ArrayList<>();

                for (int ant = 0; ant < numAnts; ant++) {
                    int[] assign = new int[C];
                    double[] load = new double[V];

                    for (int i = 0; i < C; i++) {
                        double sum = 0;
                        double[] probs = new double[V];
                        for (int j = 0; j < V; j++) {
                            probs[j] = Math.pow(pheromone[i][j], alpha) * Math.pow(heuristic[i][j], beta);
                            sum += probs[j];
                        }
                        double pick = rand.nextDouble() * sum;
                        double cum = 0;
                        int chosen = 0;
                        for (int j = 0; j < V; j++) {
                            cum += probs[j];
                            if (cum >= pick) {
                                chosen = j;
                                break;
                            }
                        }
                        assign[i] = chosen;
                        load[chosen] += cloudletList.get(i).getCloudletLength() / (double) vmlist.get(chosen).getMips();
                    }
                    double makespan = Arrays.stream(load).max().orElse(0.0);
                    solList.add(assign);
                    makespans.add(makespan);
                    if (makespan < bestMakespan) {
                        bestMakespan = makespan;
                        bestAssign = assign.clone();
                    }
                }

                // Evaporation & deposition
                for (int i = 0; i < C; i++)
                    for (int j = 0; j < V; j++)
                        pheromone[i][j] *= (1 - evap);

                for (int a = 0; a < numAnts; a++) {
                    double contrib = Q / makespans.get(a);
                    int[] sol = solList.get(a);
                    for (int i = 0; i < C; i++) {
                        pheromone[i][sol[i]] += contrib;
                    }
                }
            }

            // Assign best solution
            for (int i = 0; i < C; i++) {
                cloudletList.get(i).setGuestId(vmlist.get(bestAssign[i]).getId());
            }
            // ========================
            // END ACO
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
//            AttackSimulator.simulateInferenceAttack(results);
            PrivacyAttackSimulator.simulateInferenceAttack(results);

            // Privacy analysis
            PrivacyMonitor.checkIOLarge(results);
            PrivacyMonitor.applyDifferentialPrivacy(0.2);
            PrivacyMonitor.applyDifferentialPrivacy(0.5);

            printCloudletList(results);
            Log.println("Simulation completed with enhanced security & privacy");
            Log.println("CloudSimACOEnhanced finished!");

        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Simulation terminated: " + e.getMessage());
        }
    }

    /**
     * Creates a list of VMs for the given user.
     *
     * @param userId the ID of the user submitting the VMs
     * @param vms    number of VMs to create
     * @return list of initialized VMs
     */
    private static List<Vm> createVM(int userId, final int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000; // image size (MB)
        int ram = 512;
        int mips = 1000;
        long bw = 1000;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < vms; i++) {
            list.add(new Vm(i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared()));
        }
        return list;
    }

    /**
     * Creates a list of cloudlets for the given user.
     *
     * @param userId    the ID of the user submitting the cloudlets
     * @param cloudlets number of cloudlets to create
     * @return list of initialized cloudlets
     */
    private static List<Cloudlet> createCloudlet(int userId, final int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        long length = 1000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel um = new UtilizationModelFull();

        for (int i = 0; i < cloudlets; i++) {
            Cloudlet cl = new Cloudlet(i, length, pesNumber, fileSize, outputSize, um, um, um);
            cl.setUserId(userId);
            list.add(cl);
        }
        return list;
    }

    /**
     * Creates a datacenter with predefined hosts, characteristics, and policies.
     *
     * @param name the name of the datacenter
     * @return initialized Datacenter instance
     * @throws Exception if datacenter creation fails
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();

        // Create processing elements for hosts
        List<Pe> peList1 = new ArrayList<>();
        int mips = 1000;
        for (int i = 0; i < 4; i++)
            peList1.add(new Pe(i, new PeProvisionerSimple(mips)));

        List<Pe> peList2 = new ArrayList<>();
        for (int i = 0; i < 2; i++)
            peList2.add(new Pe(i, new PeProvisionerSimple(mips)));

        int hostId = 0;
        int ram = 2048;
        long storage = 1000000;
        int bw = 10000;

        hostList.add(new Host(hostId++, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList1,
                new VmSchedulerTimeShared(peList1)));
        hostList.add(new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList2,
                new VmSchedulerTimeShared(peList2)));

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.1;
        double costPerBw = 0.1;
        LinkedList<Storage> storageList = new LinkedList<>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList, time_zone,
                cost, costPerMem, costPerStorage, costPerBw);

        return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
    }

    /**
     * Prints the list of cloudlets executed, their status, and timing information.
     *
     * @param list the list of completed cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) {
        Log.println();
        Log.println("========== OUTPUT ==========");

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Cloudlet ID", "STATUS", "DC ID", "VM ID", "Time", "Start Time", "Finish Time" });

        DecimalFormat df = new DecimalFormat("###.##");
        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                rows.add(new String[] { String.valueOf(c.getCloudletId()), "SUCCESS", String.valueOf(c.getResourceId()),
                        String.valueOf(c.getGuestId()), df.format(c.getActualCPUTime()),
                        df.format(c.getExecStartTime()), df.format(c.getExecFinishTime()) });
            }
        }

        int cols = rows.get(0).length;
        int[] widths = new int[cols];
        for (String[] row : rows)
            for (int i = 0; i < cols; i++)
                widths[i] = Math.max(widths[i], row[i].length());

        StringBuilder fmt = new StringBuilder();
        for (int w : widths)
            fmt.append(" %-"+w+"s ");
        String formatLine = fmt.toString().trim();

        Log.println(String.format(formatLine, (Object[]) rows.get(0)));
        StringBuilder sep = new StringBuilder();
        for (int w : widths) {
            sep.append(" ");
            for (int i = 0; i < w; i++)
                sep.append("-");
            sep.append(" ");
        }
        Log.println(sep.toString().trim());

        for (int i = 1; i < rows.size(); i++)
            Log.println(String.format(formatLine, (Object[]) rows.get(i)));
    }
}
