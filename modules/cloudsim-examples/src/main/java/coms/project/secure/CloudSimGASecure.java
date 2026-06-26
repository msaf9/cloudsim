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

import coms.project.attack.AttackSimulator;
import coms.project.attack.CryptoUtils;
import coms.project.monitor.AccessControl;
import coms.project.monitor.PrivacyMonitor;
import coms.project.monitor.SecurityMonitor;

/**
 * Entry point for the enhanced CloudSim simulation incorporating genetic
 * algorithm based scheduling alongside security and privacy measures.
 */
public class CloudSimGASecure {
    
    /** The broker responsible for submitting VMs and Cloudlets to datacenters. */
    public static DatacenterBroker broker;
    
    /** The list of cloudlets to be scheduled and executed. */
    private static List<Cloudlet> cloudletList;
    
    /** The list of virtual machines available for scheduling cloudlets. */
    private static List<Vm> vmlist;
    
    /** Access control manager for user permissions. */
    private static AccessControl acl;
    
    /** Maximum allowed number of cloudlets per submission to enforce quota. */
    private static final int MAX_CLOUDLETS = 50;

    /**
     * Main method that initializes CloudSim, sets up resources, simulates attacks,
     * applies genetic algorithm scheduling, and monitors security/privacy.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimGAEnhanced...");
        try {
            // Initialize access control list and cryptographic utilities
            acl = new AccessControl();
            CryptoUtils.init();

            // Initialize CloudSim library
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create two datacenters
            Datacenter dc0 = createDatacenter("Datacenter_0");
            Datacenter dc1 = createDatacenter("Datacenter_1");

            // Create broker, register with ACL
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();
            acl.registerUser(brokerId, "BROKER");

            // Create VMs and cloudlets
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // Encrypt VM metadata and simulate various attacks
            AttackSimulator.encryptVMLocalMetadata(vmlist);
            AttackSimulator.simulateDoS(broker, 30);
            AttackSimulator.simulatePoisoning(cloudletList);
            AttackSimulator.simulateDataLeakageEncrypted(vmlist);
            AttackSimulator.simulateTimingAnalysis(broker);

            // Enforce submission quota
            if (cloudletList.size() > MAX_CLOUDLETS) {
                throw new SecurityException("Submission quota exceeded: " + cloudletList.size());
            }

            // Submit resources if ACL permits
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
            // GA‐BASED SCHEDULING
            // ========================
            int populationSize = 50;
            int generations = 100;
            int[] bestMapping = GeneticAlgorithm.schedule(cloudletList, vmlist, populationSize, generations);

            // Assign best VM mapping for each cloudlet
            for (int i = 0; i < cloudletList.size(); i++) {
                cloudletList.get(i).setGuestId(vmlist.get(bestMapping[i]).getId());
            }
            // ========================
            // END GA
            // ========================

            // Run the simulation
            CloudSim.startSimulation();
            List<Cloudlet> results = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // Perform security monitoring and privacy checks
            double avg = SecurityMonitor.calculateAverageExecTime(results);
            double threshold = avg * 3;
            SecurityMonitor.checkExecutionAnomalies(results, threshold);
            SecurityMonitor.checkWorkloadDistribution(results, threshold);
            SecurityMonitor.checkPrivacyLeaksEncrypted(results);

            // Simulate inference attack and apply privacy measures
            AttackSimulator.simulateInferenceAttack(results);
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
     * Encapsulates the genetic algorithm for scheduling cloudlets to VMs.
     */
    public static class GeneticAlgorithm {
        private static final Random rand = new Random();

        /**
         * Runs the genetic algorithm to find an optimal mapping of cloudlets to VMs.
         *
         * @param cloudlets    list of cloudlets to schedule
         * @param vms          list of available VMs
         * @param popSize      size of the GA population
         * @param generations  number of GA generations to evolve
         * @return integer array mapping each cloudlet index to a VM index
         */
        public static int[] schedule(List<Cloudlet> cloudlets, List<Vm> vms, int popSize, int generations) {
            int numCl = cloudlets.size(), numVMs = vms.size();
            List<int[]> population = new ArrayList<>();
            // Initialize population with random assignments
            for (int i = 0; i < popSize; i++) {
                int[] ind = new int[numCl];
                for (int j = 0; j < numCl; j++) {
                    ind[j] = rand.nextInt(numVMs);
                }
                population.add(ind);
            }

            int[] best = new int[numCl];
            double bestFit = Double.NEGATIVE_INFINITY;

            // Evolve population over generations
            for (int gen = 0; gen < generations; gen++) {
                double[] fitness = new double[popSize];
                for (int i = 0; i < popSize; i++) {
                    fitness[i] = evaluate(population.get(i), cloudlets, vms);
                    if (fitness[i] > bestFit) {
                        bestFit = fitness[i];
                        best = population.get(i).clone();
                    }
                }

                // Create next generation via tournament, crossover, and mutation
                List<int[]> newPop = new ArrayList<>();
                while (newPop.size() < popSize) {
                    int[] p1 = tournament(population, fitness);
                    int[] p2 = tournament(population, fitness);
                    int[] child = crossover(p1, p2);
                    mutate(child, numVMs);
                    newPop.add(child);
                }
                population = newPop;
            }
            return best;
        }

        /**
         * Evaluates fitness of an individual mapping based on makespan.
         *
         * @param ind        array mapping cloudlets to VMs
         * @param cloudlets  list of cloudlets
         * @param vms        list of VMs
         * @return fitness value (higher is better)
         */
        private static double evaluate(int[] ind, List<Cloudlet> cloudlets, List<Vm> vms) {
            double[] load = new double[vms.size()];
            // Compute load per VM
            for (int i = 0; i < ind.length; i++) {
                load[ind[i]] += cloudlets.get(i).getCloudletLength()
                        / (double) vms.get(ind[i]).getMips();
            }
            double makespan = Arrays.stream(load).max().orElse(0.0);
            return 1.0 / makespan;
        }

        /**
         * Selects one parent via tournament selection.
         *
         * @param pop  current population
         * @param fit  array of fitness values
         * @return selected individual
         */
        private static int[] tournament(List<int[]> pop, double[] fit) {
            int i1 = rand.nextInt(pop.size());
            int i2 = rand.nextInt(pop.size());
            return fit[i1] > fit[i2] ? pop.get(i1) : pop.get(i2);
        }

        /**
         * Performs one-point crossover between two parents.
         *
         * @param p1  first parent individual
         * @param p2  second parent individual
         * @return child individual
         */
        private static int[] crossover(int[] p1, int[] p2) {
            int len = p1.length, cp = rand.nextInt(len);
            int[] child = new int[len];
            System.arraycopy(p1, 0, child, 0, cp);
            System.arraycopy(p2, cp, child, cp, len - cp);
            return child;
        }

        /**
         * Mutates an individual by randomly reassigning cloudlet indices.
         *
         * @param ind    individual to mutate
         * @param numVMs total number of VMs available
         */
        private static void mutate(int[] ind, int numVMs) {
            double pm = 1.0 / ind.length;
            for (int i = 0; i < ind.length; i++) {
                if (rand.nextDouble() < pm) {
                    ind[i] = rand.nextInt(numVMs);
                }
            }
        }
    }

    /**
     * Creates a list of homogeneous VMs for the given user.
     *
     * @param userId ID of the user/broker owning the VMs
     * @param vms    number of VMs to create
     * @return list of configured {@link Vm} instances
     */
    private static List<Vm> createVM(int userId, int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000;
        int ram = 512, mips = 1000, bw = 1000;
        for (int i = 0; i < vms; i++) {
            list.add(new Vm(i, userId, mips, 1, ram, bw, size, "Xen",
                    new CloudletSchedulerTimeShared()));
        }
        return list;
    }

    /**
     * Creates a list of identical cloudlets for the given user.
     *
     * @param userId    ID of the user/broker owning the cloudlets
     * @param cloudlets number of cloudlets to create
     * @return list of configured {@link Cloudlet} instances
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
     * Constructs a datacenter with two hosts of differing capacities.
     *
     * @param name unique name for the datacenter
     * @return configured {@link Datacenter} instance
     * @throws Exception if datacenter creation fails
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hosts = new ArrayList<>();
        int mips = 1000;

        // Host with 4 PEs
        List<Pe> p1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            p1.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        // Host with 2 PEs
        List<Pe> p2 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            p2.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        hosts.add(new Host(0, new RamProvisionerSimple(2048),
                new BwProvisionerSimple(10000), 1000000, p1,
                new VmSchedulerTimeShared(p1)));
        hosts.add(new Host(1, new RamProvisionerSimple(2048),
                new BwProvisionerSimple(10000), 1000000, p2,
                new VmSchedulerTimeShared(p2)));

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hosts,
                0, 3.0, 0.05, 0.1, 0.1);

        return new Datacenter(name, chars, new VmAllocationPolicySimple(hosts),
                new LinkedList<Storage>(), 0);
    }

    /**
     * Prints the list of completed cloudlets in a formatted table.
     *
     * @param list list of executed {@link Cloudlet} instances
     */
    private static void printCloudletList(List<Cloudlet> list) {
        // Determine column widths
        int[] w = { "Cloudlet ID".length(), "STATUS".length(),
                "DC ID".length(), "VM ID".length(),
                "Time".length(), "Start".length(), "Finish".length() };
        DecimalFormat df = new DecimalFormat("###.##");

        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                w[0] = Math.max(w[0], Integer.toString(c.getCloudletId()).length());
                w[1] = Math.max(w[1], 7);
                w[2] = Math.max(w[2], Integer.toString(c.getResourceId()).length());
                w[3] = Math.max(w[3], Integer.toString(c.getGuestId()).length());
                w[4] = Math.max(w[4], df.format(c.getActualCPUTime()).length());
                w[5] = Math.max(w[5], df.format(c.getExecStartTime()).length());
                w[6] = Math.max(w[6], df.format(c.getExecFinishTime()).length());
            }
        }

        // Build format string
        StringBuilder fmt = new StringBuilder();
        for (int width : w) {
            fmt.append(" %-" + width + "s");
        }

        // Print header
        Log.println(String.format(fmt.toString(),
                "Cloudlet ID", "STATUS", "DC ID", "VM ID", "Time", "Start", "Finish"));

        // Print each completed cloudlet
        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.println(String.format(fmt.toString(),
                        c.getCloudletId(), "SUCCESS",
                        c.getResourceId(), c.getGuestId(),
                        df.format(c.getActualCPUTime()),
                        df.format(c.getExecStartTime()),
                        df.format(c.getExecFinishTime())));
            }
        }
    }
}
