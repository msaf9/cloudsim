package coms.project.enhanced;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
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
 * Main class for running an enhanced CloudSim simulation integrating security
 * and privacy measures along with a Whale Optimization Algorithm (WOA) for
 * cloudlet-to-VM scheduling.
 */
public class CloudSimWOAEnhanced {
    /** The broker responsible for submitting VMs and cloudlets. */
    public static DatacenterBroker broker;

    /** List of cloudlets to be executed. */
    private static List<Cloudlet> cloudletList;

    /** List of virtual machines. */
    private static List<Vm> vmlist;

    /** Access control manager. */
    private static AccessControl acl;

    /** Maximum allowed number of cloudlets to submit. */
    private static final int MAX_CLOUDLETS = 50;

    /**
     * Entry point for the simulation.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimWOAEnhanced...");
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
            // WOA Scheduling
            // ========================
            int numWhales = 30;
            int maxIter = 50;
            int nC = cloudletList.size();
            int nV = vmlist.size();
            int[] bestAssignment = WhaleOptimization.optimize(
                numWhales, maxIter, nC, nV, cloudletList, vmlist
            );
            for (int i = 0; i < nC; i++) {
                int clId = cloudletList.get(i).getCloudletId();
                int vmIndex = bestAssignment[i];
                int vmId = vmlist.get(vmIndex).getId();
                broker.bindCloudletToVm(clId, vmId);
            }
            // ========================
            // END WOA
            // ========================

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

            // Membership-inference privacy attack
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
     * Implements the Whale Optimization Algorithm for scheduling.
     */
    static class WhaleOptimization {
        /** Random number generator for stochastic components. */
        private static Random rand = new Random();

        /**
         * Optimizes the assignment of cloudlets to VMs using WOA.
         *
         * @param numWhales   number of whale agents
         * @param maxIter     maximum number of iterations
         * @param nCloudlets  total number of cloudlets
         * @param nVms        total number of VMs
         * @param cloudlets   list of cloudlets to schedule
         * @param vms         list of available VMs
         * @return an array of size nCloudlets where each entry is the index
         *         of the VM to which the corresponding cloudlet is assigned
         */
        public static int[] optimize(int numWhales, int maxIter, int nCloudlets, int nVms,
                                     List<Cloudlet> cloudlets, List<Vm> vms) {
            double[][] X = new double[numWhales][nCloudlets];
            double[] fitness = new double[numWhales];

            // Initialize whales
            for (int i = 0; i < numWhales; i++) {
                for (int j = 0; j < nCloudlets; j++) {
                    X[i][j] = rand.nextDouble() * nVms;
                }
                fitness[i] = evaluate(X[i], cloudlets, vms);
            }
            int bestIdx = argMin(fitness);
            double[] Xbest = X[bestIdx].clone();

            // Main optimization loop
            for (int t = 1; t <= maxIter; t++) {
                double a = 2.0 * (1.0 - (double) t / maxIter);
                for (int i = 0; i < numWhales; i++) {
                    for (int j = 0; j < nCloudlets; j++) {
                        double r1 = rand.nextDouble(), r2 = rand.nextDouble();
                        double A = 2 * a * r1 - a;
                        double C = 2 * r2;
                        double p = rand.nextDouble();
                        double D, newPos;
                        if (p < 0.5) {
                            if (Math.abs(A) < 1) {
                                D = Math.abs(C * Xbest[j] - X[i][j]);
                                newPos = Xbest[j] - A * D;
                            } else {
                                int randIdx = rand.nextInt(numWhales);
                                D = Math.abs(C * X[randIdx][j] - X[i][j]);
                                newPos = X[randIdx][j] - A * D;
                            }
                        } else {
                            double b = 1.0;
                            double l = rand.nextDouble() * 2 - 1;
                            D = Math.abs(Xbest[j] - X[i][j]);
                            newPos = D * Math.exp(b * l) * Math.cos(2 * Math.PI * l) + Xbest[j];
                        }
                        X[i][j] = clamp(newPos, 0, nVms - 1e-6);
                    }
                    fitness[i] = evaluate(X[i], cloudlets, vms);
                }
                int idx = argMin(fitness);
                if (fitness[idx] < fitness[bestIdx]) {
                    bestIdx = idx;
                    Xbest = X[bestIdx].clone();
                }
            }

            // Build final integer assignment
            int[] assignment = new int[nCloudlets];
            for (int j = 0; j < nCloudlets; j++) {
                assignment[j] = (int) Math.floor(Xbest[j]);
            }
            return assignment;
        }

        /**
         * Evaluates a given solution by computing the makespan.
         *
         * @param sol       continuous position vector for whale
         * @param cloudlets list of cloudlets
         * @param vms       list of VMs
         * @return the makespan (maximum total execution time on any VM)
         */
        private static double evaluate(double[] sol, List<Cloudlet> cloudlets, List<Vm> vms) {
            int nV = vms.size();
            double[] load = new double[nV];
            for (int i = 0; i < sol.length; i++) {
                int vm = (int) Math.floor(sol[i]);
                load[vm] += cloudlets.get(i).getCloudletLength() / (double) vms.get(vm).getMips();
            }
            return Arrays.stream(load).max().orElse(0.0);
        }

        /**
         * Finds the index of the minimum element in an array.
         *
         * @param arr array of double values
         * @return index of the smallest value
         */
        private static int argMin(double[] arr) {
            int idx = 0;
            for (int i = 1; i < arr.length; i++) {
                if (arr[i] < arr[idx]) {
                    idx = i;
                }
            }
            return idx;
        }

        /**
         * Clamps a value within specified bounds.
         *
         * @param x  the value to clamp
         * @param lo lower bound (inclusive)
         * @param hi upper bound (inclusive)
         * @return clamped value
         */
        private static double clamp(double x, double lo, double hi) {
            return x < lo ? lo : (x > hi ? hi : x);
        }
    }

    /**
     * Creates a list of homogeneous virtual machines.
     *
     * @param userId identifier of the VM owner
     * @param vms    number of VMs to create
     * @return list of VMs
     */
    private static List<Vm> createVM(int userId, int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000; // image size (MB)
        int ram = 512;     // VM memory (MB)
        int mips = 1000;   // processing power
        int bw = 1000;     // bandwidth (Mbps)
        for (int i = 0; i < vms; i++) {
            list.add(new Vm(i, userId, mips, 1, ram, bw, size, "Xen", new CloudletSchedulerSpaceShared()));
        }
        return list;
    }

    /**
     * Creates a list of cloudlets with full utilization.
     *
     * @param userId    identifier of the cloudlet owner
     * @param cloudlets number of cloudlets to create
     * @return list of Cloudlet objects
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        long length = 1000;    // total instructions
        long fileSize = 300;   // input file size (MB)
        long outputSize = 300; // output file size (MB)
        UtilizationModel um = new UtilizationModelFull();
        for (int i = 0; i < cloudlets; i++) {
            Cloudlet cl = new Cloudlet(i, length, 1, fileSize, outputSize, um, um, um);
            cl.setUserId(userId);
            list.add(cl);
        }
        return list;
    }

    /**
     * Creates a datacenter with two hosts.
     *
     * @param name name of the datacenter
     * @return configured Datacenter instance
     * @throws Exception in case of configuration errors
     */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hosts = new ArrayList<>();
        int mips = 1000;

        // First host with 4 processing elements
        List<Pe> p1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            p1.add(new Pe(i, new PeProvisionerSimple(mips)));
        }
        hosts.add(new Host(
            0,
            new RamProvisionerSimple(2048),
            new BwProvisionerSimple(10000),
            1000000,
            p1,
            new VmSchedulerTimeShared(p1)
        ));

        // Second host with 2 processing elements
        List<Pe> p2 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            p2.add(new Pe(i, new PeProvisionerSimple(mips)));
        }
        hosts.add(new Host(
            1,
            new RamProvisionerSimple(2048),
            new BwProvisionerSimple(10000),
            1000000,
            p2,
            new VmSchedulerTimeShared(p2)
        ));

        DatacenterCharacteristics chars = new DatacenterCharacteristics(
            "x86",
            "Linux",
            "Xen",
            hosts,
            0,
            3.0,    // time zone
            0.05,   // cost per second
            0.1,    // cost per memory
            0.1     // cost per storage
        );
        return new Datacenter(name, chars, new VmAllocationPolicySimple(hosts),
                              new LinkedList<Storage>(), 0);
    }

    /**
     * Prints the list of completed cloudlets in a formatted table.
     *
     * @param list list of executed Cloudlet objects
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
