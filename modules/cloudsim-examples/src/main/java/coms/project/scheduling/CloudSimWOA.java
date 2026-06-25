package coms.project.scheduling;

import java.text.DecimalFormat;
import java.util.ArrayList;
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

/**
 * CloudSimWOA simulates a cloud environment using the CloudSim framework.
 * It uses the Whale Optimization Algorithm (WOA) to schedule cloudlets across virtual machines (VMs).
 * Two datacenters are created, and a set of cloudlets are assigned to VMs using WOA.
 */
public class CloudSimWOA {

    /** The DatacenterBroker responsible for managing cloudlets and VMs. */
    public static DatacenterBroker broker;

    /** List of cloudlets to be processed. */
    private static List<Cloudlet> cloudletList;

    /** List of virtual machines used in the simulation. */
    private static List<Vm> vmlist;

    /**
     * Main method to initiate the simulation.
     *
     * @param args Not used.
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimWOA...");

        try {
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // disable event tracing

            // Initialize the CloudSim library
            CloudSim.init(num_user, calendar, trace_flag);

            // Create two datacenters
            Datacenter datacenter0 = createDatacenter("Datacenter_0");
            Datacenter datacenter1 = createDatacenter("Datacenter_1");

            // Create broker
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // Create VMs and Cloudlets and send them to the broker
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            broker.submitGuestList(vmlist);
            broker.submitCloudletList(cloudletList);

            // ====== WOA SCHEDULING ======
            int numWhales = 30;
            int maxIter = 50;
            int nC = cloudletList.size();
            int nV = vmlist.size();

            int[] bestAssignment = WhaleOptimization.optimize(numWhales, maxIter, nC, nV, cloudletList, vmlist);

            for (int i = 0; i < nC; i++) {
                int cloudletId = cloudletList.get(i).getCloudletId();
                int vmIndex = bestAssignment[i];
                int vmId = vmlist.get(vmIndex).getId();
                broker.bindCloudletToVm(cloudletId, vmId);
            }
            // ====== END WOA SCHEDULING ======

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> newList = broker.getCloudletReceivedList();
            printCloudletList(newList);

            Log.println("CloudSimWOA finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("The simulation has been terminated due to an unexpected error");
        }
    }

    /**
     * Implements the Whale Optimization Algorithm for VM allocation.
     */
    static class WhaleOptimization {
        private static Random rand = new Random();

        /**
         * Executes the Whale Optimization Algorithm.
         *
         * @param numWhales  Number of whales (candidate solutions)
         * @param maxIter    Maximum number of iterations
         * @param nCloudlets Number of cloudlets
         * @param nVms       Number of virtual machines
         * @param cloudlets  List of cloudlets
         * @param vms        List of virtual machines
         * @return An array mapping each cloudlet to a VM index
         */
        public static int[] optimize(int numWhales, int maxIter, int nCloudlets, int nVms,
                                     List<Cloudlet> cloudlets, List<Vm> vms) {
            double[][] X = new double[numWhales][nCloudlets];
            double[] fitness = new double[numWhales];

            for (int i = 0; i < numWhales; i++) {
                for (int j = 0; j < nCloudlets; j++) {
                    X[i][j] = rand.nextDouble() * nVms;
                }
                fitness[i] = evaluate(X[i], cloudlets, vms);
            }

            int bestIdx = argMin(fitness);
            double[] Xbest = X[bestIdx].clone();

            for (int t = 1; t <= maxIter; t++) {
                double a = 2.0 * (1.0 - (double) t / maxIter);
                for (int i = 0; i < numWhales; i++) {
                    for (int j = 0; j < nCloudlets; j++) {
                        double r1 = rand.nextDouble();
                        double r2 = rand.nextDouble();
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

            int[] assignment = new int[nCloudlets];
            for (int j = 0; j < nCloudlets; j++) {
                assignment[j] = (int) Math.floor(Xbest[j]);
            }

            return assignment;
        }

        /**
         * Evaluates the fitness of a solution by calculating the makespan.
         *
         * @param sol       Solution array
         * @param cloudlets List of cloudlets
         * @param vms       List of VMs
         * @return The makespan value
         */
        private static double evaluate(double[] sol, List<Cloudlet> cloudlets, List<Vm> vms) {
            int nV = vms.size();
            double[] load = new double[nV];

            for (int i = 0; i < sol.length; i++) {
                int vm = (int) Math.floor(sol[i]);
                double len = cloudlets.get(i).getCloudletLength();
                double mips = vms.get(vm).getMips();
                load[vm] += len / mips;
            }

            double makespan = 0;
            for (double l : load) {
                if (l > makespan) makespan = l;
            }

            return makespan;
        }

        /**
         * Finds the index of the smallest value in an array.
         *
         * @param arr The input array
         * @return Index of the minimum value
         */
        private static int argMin(double[] arr) {
            int idx = 0;
            for (int i = 1; i < arr.length; i++) {
                if (arr[i] < arr[idx]) idx = i;
            }
            return idx;
        }

        /**
         * Clamps a value within a specified range.
         *
         * @param x  Value to clamp
         * @param lo Minimum value
         * @param hi Maximum value
         * @return Clamped value
         */
        private static double clamp(double x, double lo, double hi) {
            return x < lo ? lo : (x > hi ? hi : x);
        }
    }

    /**
     * Creates a list of VMs for the simulation.
     *
     * @param userId ID of the user/broker
     * @param vms    Number of VMs to create
     * @return List of VMs
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
            list.add(new Vm(i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared()));
        }
        return list;
    }

    /**
     * Creates a list of cloudlets (tasks) for the simulation.
     *
     * @param userId    ID of the user/broker
     * @param cloudlets Number of cloudlets to create
     * @return List of cloudlets
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        long length = 1000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < cloudlets; i++) {
            Cloudlet cl = new Cloudlet(i, length, pesNumber, fileSize, outputSize,
                    utilizationModel, utilizationModel, utilizationModel);
            cl.setUserId(userId);
            list.add(cl);
        }
        return list;
    }

    /**
     * Creates a datacenter with a given name.
     *
     * @param name Name of the datacenter
     * @return Datacenter instance
     */
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList1 = new ArrayList<>();
        int mips = 1000;

        for (int i = 0; i < 4; i++) {
            peList1.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        List<Pe> peList2 = new ArrayList<>();
        peList2.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList2.add(new Pe(1, new PeProvisionerSimple(mips)));

        int hostId = 0;
        int ram = 2048;
        long storage = 1000000;
        int bw = 10000;

        hostList.add(new Host(hostId++, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage,
                peList1, new VmSchedulerTimeShared(peList1)));

        hostList.add(new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage,
                peList2, new VmSchedulerTimeShared(peList2)));

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.1;
        double costPerBw = 0.1;

        LinkedList<Storage> storageList = new LinkedList<>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm,
                hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics,
                    new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    /**
     * Prints details of completed cloudlets.
     *
     * @param list List of completed cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) {
        int maxIdWidth = "Cloudlet ID".length();
        int maxStatusWidth = "STATUS".length();
        int maxDcIdWidth = "Data center ID".length();
        int maxVmIdWidth = "VM ID".length();
        int maxTimeWidth = "Time".length();
        int maxStartWidth = "Start Time".length();
        int maxFinishWidth = "Finish Time".length();

        DecimalFormat dft = new DecimalFormat("###.##");

        for (Cloudlet cl : list) {
            maxIdWidth = Math.max(maxIdWidth, Integer.toString(cl.getCloudletId()).length());
            maxStatusWidth = Math.max(maxStatusWidth, cl.getStatus().toString().length());
            maxDcIdWidth = Math.max(maxDcIdWidth, Integer.toString(cl.getResourceId()).length());
            maxVmIdWidth = Math.max(maxVmIdWidth, Integer.toString(cl.getGuestId()).length());
            maxTimeWidth = Math.max(maxTimeWidth, dft.format(cl.getActualCPUTime()).length());
            maxStartWidth = Math.max(maxStartWidth, dft.format(cl.getExecStartTime()).length());
            maxFinishWidth = Math.max(maxFinishWidth, dft.format(cl.getExecFinishTime()).length());
        }

        String fmt = String.format("%%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds%n", maxIdWidth,
                maxStatusWidth, maxDcIdWidth, maxVmIdWidth, maxTimeWidth, maxStartWidth, maxFinishWidth);

        Log.println();
        Log.println("================================= OUTPUT =================================");
        Log.println(String.format(fmt, "Cloudlet ID", "STATUS", "Data center ID", "VM ID",
                "Time", "Start Time", "Finish Time"));

        for (Cloudlet cl : list) {
            if (cl.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.println(String.format(fmt, cl.getCloudletId(), "SUCCESS", cl.getResourceId(),
                        cl.getGuestId(), dft.format(cl.getActualCPUTime()),
                        dft.format(cl.getExecStartTime()), dft.format(cl.getExecFinishTime())));
            }
        }
    }
}
