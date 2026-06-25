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
 * CloudSimPSOEnhanced simulates a secure and privacy-aware cloud computing environment
 * using CloudSim and Particle Swarm Optimization (PSO) for task scheduling.
 * It includes mechanisms for access control, simulated attacks, and anomaly monitoring.
 */
public class CloudSimPSOEnhanced {

    /** Broker responsible for submitting and managing cloudlets and VMs */
    public static DatacenterBroker broker;

    /** List of cloudlets to be submitted */
    private static List<Cloudlet> cloudletList;

    /** List of virtual machines */
    private static List<Vm> vmlist;

    /** Access control list manager */
    private static AccessControl acl;

    /** Maximum allowed cloudlet submissions */
    private static final int MAX_CLOUDLETS = 50;

    /**
     * Main method to run the CloudSim simulation with PSO-based scheduling and enhanced security.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimPSOEnhanced...");
        try {
            // Initialize access control and cryptographic utilities
            acl = new AccessControl();
            CryptoUtils.init();

            // Initialize CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create datacenters
            Datacenter dc0 = createDatacenter("Datacenter_0");
            Datacenter dc1 = createDatacenter("Datacenter_1");

            // Create broker and register it
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();
            acl.registerUser(brokerId, "BROKER");

            // Create virtual machines and cloudlets
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // Encrypt VM metadata
            AttackSimulator.encryptVMLocalMetadata(vmlist);

            // Simulate various types of attacks
            AttackSimulator.simulateDoS(broker, 30);
            AttackSimulator.simulatePoisoning(cloudletList);
            AttackSimulator.simulateDataLeakageEncrypted(vmlist);
            AttackSimulator.simulateTimingAnalysis(broker);

            // Enforce submission quotas
            if (cloudletList.size() > MAX_CLOUDLETS) {
                throw new SecurityException("Submission quota exceeded: " + cloudletList.size());
            }

            // Submit resources if access is allowed
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

            // Run PSO-based scheduling
            int[] assignment = runPsoScheduling(cloudletList.size(), vmlist.size());
            for (int i = 0; i < cloudletList.size(); i++) {
                cloudletList.get(i).setGuestId(vmlist.get(assignment[i]).getId());
            }

            // Start simulation
            CloudSim.startSimulation();
            List<Cloudlet> results = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // Run security and privacy checks
            double avg = SecurityMonitor.calculateAverageExecTime(results);
            double threshold = avg * 3;
            SecurityMonitor.checkExecutionAnomalies(results, threshold);
            SecurityMonitor.checkWorkloadDistribution(results, threshold);
            SecurityMonitor.checkPrivacyLeaksEncrypted(results);

            // Simulate inference attacks
            AttackSimulator.simulateInferenceAttack(results);

            // Perform privacy analysis
            PrivacyMonitor.checkIOLarge(results);
            PrivacyMonitor.applyDifferentialPrivacy(0.2);
            PrivacyMonitor.applyDifferentialPrivacy(0.5);

            // Output results
            printCloudletList(results);
            Log.println("Simulation completed with enhanced security & privacy");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Simulation terminated: " + e.getMessage());
        }
    }

    /**
     * Executes Particle Swarm Optimization (PSO) for task scheduling to minimize maximum load.
     *
     * @param numCloudlets the number of cloudlets to be scheduled
     * @param numVms the number of available VMs
     * @return array of VM assignments for each cloudlet
     */
    private static int[] runPsoScheduling(int numCloudlets, int numVms) {
        final int SWARM_SIZE = 30, MAX_ITER = 100;
        final double W = 0.7, C1 = 1.5, C2 = 1.5;
        Random rand = new Random();

        class Particle {
            double[] pos = new double[numCloudlets];
            double[] vel = new double[numCloudlets];
            double[] bestPos = new double[numCloudlets];
            double bestFit = Double.MAX_VALUE;
        }

        List<Particle> swarm = new ArrayList<>();
        Particle globalBest = new Particle();
        globalBest.bestFit = Double.MAX_VALUE;

        // Initialize swarm
        for (int i = 0; i < SWARM_SIZE; i++) {
            Particle p = new Particle();
            for (int d = 0; d < numCloudlets; d++) {
                p.pos[d] = rand.nextDouble() * numVms;
                p.vel[d] = rand.nextDouble() - 0.5;
                p.bestPos[d] = p.pos[d];
            }
            p.bestFit = evaluateFitness(p.pos, numCloudlets, numVms);
            if (p.bestFit < globalBest.bestFit) {
                globalBest.bestFit = p.bestFit;
                System.arraycopy(p.pos, 0, globalBest.bestPos, 0, numCloudlets);
            }
            swarm.add(p);
        }

        // PSO loop
        for (int iter = 0; iter < MAX_ITER; iter++) {
            for (Particle p : swarm) {
                for (int d = 0; d < numCloudlets; d++) {
                    double r1 = rand.nextDouble(), r2 = rand.nextDouble();
                    p.vel[d] = W * p.vel[d] + C1 * r1 * (p.bestPos[d] - p.pos[d])
                            + C2 * r2 * (globalBest.bestPos[d] - p.pos[d]);
                    p.pos[d] += p.vel[d];
                    if (p.pos[d] < 0) p.pos[d] = 0;
                    if (p.pos[d] >= numVms) p.pos[d] = numVms - 1e-6;
                }
                double fit = evaluateFitness(p.pos, numCloudlets, numVms);
                if (fit < p.bestFit) {
                    p.bestFit = fit;
                    System.arraycopy(p.pos, 0, p.bestPos, 0, numCloudlets);
                }
                if (fit < globalBest.bestFit) {
                    globalBest.bestFit = fit;
                    System.arraycopy(p.pos, 0, globalBest.bestPos, 0, numCloudlets);
                }
            }
        }

        // Extract best assignment
        int[] bestAssign = new int[numCloudlets];
        for (int d = 0; d < numCloudlets; d++) {
            bestAssign[d] = Math.min((int) Math.round(globalBest.bestPos[d]), numVms - 1);
        }
        return bestAssign;
    }

    /**
     * Evaluates the fitness of a particle position based on VM load balancing.
     *
     * @param pos array of cloudlet-to-VM assignments
     * @param numCloudlets number of cloudlets
     * @param numVms number of VMs
     * @return fitness value (lower is better)
     */
    private static double evaluateFitness(double[] pos, int numCloudlets, int numVms) {
        int[] load = new int[numVms];
        for (double v : pos) {
            int vm = Math.min(Math.max(0, (int) Math.round(v)), numVms - 1);
            load[vm]++;
        }
        int maxLoad = 0;
        for (int l : load)
            if (l > maxLoad)
                maxLoad = l;
        return maxLoad;
    }

    /**
     * Creates a list of virtual machines with predefined configuration.
     *
     * @param userId ID of the user creating the VMs
     * @param vms number of VMs to create
     * @return list of created VMs
     */
    private static List<Vm> createVM(int userId, int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000;
        int ram = 512, mips = 1000, bw = 1000;
        for (int i = 0; i < vms; i++)
            list.add(new Vm(i, userId, mips, 1, ram, bw, size, "Xen", new CloudletSchedulerTimeShared()));
        return list;
    }

    /**
     * Creates a list of cloudlets with fixed workload settings.
     *
     * @param userId ID of the user creating the cloudlets
     * @param cloudlets number of cloudlets to create
     * @return list of created cloudlets
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        long length = 1000, fileSize = 300, outputSize = 300;
        UtilizationModel um = new UtilizationModelFull();
        for (int i = 0; i < cloudlets; i++) {
            Cloudlet c = new Cloudlet(i, length, 1, fileSize, outputSize, um, um, um);
            c.setUserId(userId);
            list.add(c);
        }
        return list;
    }

    /**
     * Creates a datacenter with specified configuration and two hosts.
     *
     * @param name name of the datacenter
     * @return the created datacenter
     * @throws Exception if datacenter creation fails
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hosts = new ArrayList<>();
        int mips = 1000;

        List<Pe> p1 = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            p1.add(new Pe(i, new PeProvisionerSimple(mips)));

        List<Pe> p2 = new ArrayList<>();
        for (int i = 0; i < 2; i++)
            p2.add(new Pe(i, new PeProvisionerSimple(mips)));

        hosts.add(new Host(0, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000), 1000000, p1,
                new VmSchedulerTimeShared(p1)));
        hosts.add(new Host(1, new RamProvisionerSimple(2048), new BwProvisionerSimple(10000), 1000000, p2,
                new VmSchedulerTimeShared(p2)));

        DatacenterCharacteristics chars = new DatacenterCharacteristics("x86", "Linux", "Xen", hosts, 0,
                3.0, 0.05, 0.1, 0.1);

        return new Datacenter(name, chars, new VmAllocationPolicySimple(hosts), new LinkedList<Storage>(), 0);
    }

    /**
     * Prints execution results of cloudlets in a formatted table.
     *
     * @param list list of executed cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) {
        String[] headers = { "Cloudlet ID", "STATUS", "DC ID", "VM ID", "Time", "Start", "Finish" };
        List<String[]> rows = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("###.##");
        int cols = headers.length;
        int[] w = new int[cols];

        for (int i = 0; i < cols; i++)
            w[i] = headers[i].length();

        for (Cloudlet c : list)
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                String[] row = { String.valueOf(c.getCloudletId()), "SUCCESS", String.valueOf(c.getResourceId()),
                        String.valueOf(c.getGuestId()), df.format(c.getActualCPUTime()),
                        df.format(c.getExecStartTime()), df.format(c.getExecFinishTime()) };
                rows.add(row);
                for (int i = 0; i < cols; i++)
                    w[i] = Math.max(w[i], row[i].length());
            }

        StringBuilder fmt = new StringBuilder();
        for (int width : w)
            fmt.append(" %-" + width + "s");
        Log.println(String.format(fmt.toString(), (Object[]) headers));
        for (String[] row : rows)
            Log.println(String.format(fmt.toString(), (Object[]) row));
    }
}
