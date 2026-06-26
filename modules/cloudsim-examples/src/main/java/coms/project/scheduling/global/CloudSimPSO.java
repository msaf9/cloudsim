package coms.project.scheduling.global;

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

/**
 * CloudSimPSO demonstrates task scheduling in a cloud simulation environment using
 * Particle Swarm Optimization (PSO). It utilizes CloudSim to create data centers, VMs,
 * cloudlets, and schedules cloudlets to VMs by minimizing makespan.
 * 
 * This example is based on CloudSim Toolkit developed by The University of Melbourne.
 * 
 * @author 
 */
public class CloudSimPSO {

    /** The broker responsible for VM and cloudlet management. */
    public static DatacenterBroker broker;

    /** List of cloudlets to be scheduled. */
    private static List<Cloudlet> cloudletList;

    /** List of virtual machines (VMs). */
    private static List<Vm> vmlist;

    /**
     * Main method to start the simulation with PSO-based task scheduling.
     * 
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimPSO...");

        try {
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // whether to log events

            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);

            // Create datacenters
            Datacenter datacenter0 = createDatacenter("Datacenter_0");
            Datacenter datacenter1 = createDatacenter("Datacenter_1");

            // Create broker
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // Create and submit VMs and cloudlets
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // Execute PSO algorithm for cloudlet-to-VM assignment
            int[] assignment = runPsoScheduling(cloudletList.size(), vmlist.size());
            for (int i = 0; i < cloudletList.size(); i++) {
                cloudletList.get(i).setGuestId(vmlist.get(assignment[i]).getId());
            }

            // Submit VMs and cloudlets to broker
            broker.submitGuestList(vmlist);
            broker.submitCloudletList(cloudletList);

            // Start simulation
            CloudSim.startSimulation();
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // Output results
            printCloudletList(newList);
            Log.println("CloudSimPSO finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Simulation terminated due to an unexpected error");
        }
    }

    /**
     * Executes the Particle Swarm Optimization (PSO) algorithm to schedule cloudlets
     * on VMs such that the maximum load (makespan) is minimized.
     * 
     * @param numCloudlets total number of cloudlets
     * @param numVms total number of VMs
     * @return array of VM assignments for each cloudlet
     */
    private static int[] runPsoScheduling(int numCloudlets, int numVms) {
        final int SWARM_SIZE = 30;
        final int MAX_ITER = 100;
        final double W = 0.7;    // inertia weight
        final double C1 = 1.5;   // cognitive coefficient
        final double C2 = 1.5;   // social coefficient
        Random rand = new Random();

        /**
         * Particle class represents a solution in the search space.
         */
        class Particle {
            double[] pos = new double[numCloudlets];
            double[] vel = new double[numCloudlets];
            double[] bestPos = new double[numCloudlets];
            double bestFitness = Double.MAX_VALUE;
        }

        List<Particle> swarm = new ArrayList<>();
        Particle globalBest = new Particle();

        // Initialize particles
        for (int i = 0; i < SWARM_SIZE; i++) {
            Particle p = new Particle();
            for (int d = 0; d < numCloudlets; d++) {
                p.pos[d] = rand.nextDouble() * numVms;
                p.vel[d] = rand.nextDouble() - 0.5;
                p.bestPos[d] = p.pos[d];
            }
            p.bestFitness = evaluateFitness(p.pos, numCloudlets, numVms);
            if (p.bestFitness < globalBest.bestFitness) {
                globalBest.bestFitness = p.bestFitness;
                System.arraycopy(p.pos, 0, globalBest.bestPos, 0, numCloudlets);
            }
            swarm.add(p);
        }

        // Main PSO loop
        for (int iter = 0; iter < MAX_ITER; iter++) {
            for (Particle p : swarm) {
                for (int d = 0; d < numCloudlets; d++) {
                    double r1 = rand.nextDouble(), r2 = rand.nextDouble();
                    p.vel[d] = W * p.vel[d] + C1 * r1 * (p.bestPos[d] - p.pos[d]) +
                               C2 * r2 * (globalBest.bestPos[d] - p.pos[d]);
                    p.pos[d] += p.vel[d];
                    // Clamp positions
                    if (p.pos[d] < 0) p.pos[d] = 0;
                    if (p.pos[d] >= numVms) p.pos[d] = numVms - 1e-6;
                }
                double fitness = evaluateFitness(p.pos, numCloudlets, numVms);
                if (fitness < p.bestFitness) {
                    p.bestFitness = fitness;
                    System.arraycopy(p.pos, 0, p.bestPos, 0, numCloudlets);
                }
                if (fitness < globalBest.bestFitness) {
                    globalBest.bestFitness = fitness;
                    System.arraycopy(p.pos, 0, globalBest.bestPos, 0, numCloudlets);
                }
            }
        }

        // Generate final VM assignment
        int[] bestAssignment = new int[numCloudlets];
        for (int d = 0; d < numCloudlets; d++) {
            bestAssignment[d] = Math.min((int) Math.round(globalBest.bestPos[d]), numVms - 1);
        }
        return bestAssignment;
    }

    /**
     * Evaluates the fitness of a particle's position vector.
     * 
     * @param pos position vector representing cloudlet-to-VM mapping
     * @param numCloudlets number of cloudlets
     * @param numVms number of VMs
     * @return fitness value (makespan = max load on any VM)
     */
    private static double evaluateFitness(double[] pos, int numCloudlets, int numVms) {
        int[] load = new int[numVms];
        for (double v : pos) {
            int vm = Math.min(Math.max(0, (int) Math.round(v)), numVms - 1);
            load[vm]++;
        }
        int maxLoad = 0;
        for (int l : load) {
            if (l > maxLoad) maxLoad = l;
        }
        return maxLoad;
    }

    /**
     * Creates a list of virtual machines.
     * 
     * @param userId ID of the broker user
     * @param vms number of VMs to create
     * @return list of VMs
     */
    private static List<Vm> createVM(int userId, final int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000; // image size
        int ram = 512;     // VM memory
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
     * Creates a list of cloudlets.
     * 
     * @param userId ID of the broker user
     * @param cloudlets number of cloudlets to create
     * @return list of cloudlets
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
     * Creates a datacenter with a specified name.
     * 
     * @param name name of the datacenter
     * @return the created Datacenter object
     */
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();

        // Define processing elements
        List<Pe> peList1 = new ArrayList<>();
        int mips = 1000;
        for (int i = 0; i < 4; i++) {
            peList1.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        List<Pe> peList2 = new ArrayList<>();
        peList2.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList2.add(new Pe(1, new PeProvisionerSimple(mips)));

        // Define hosts
        int ram = 2048;
        long storage = 1000000;
        int bw = 10000;
        hostList.add(new Host(0, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw),
                storage, peList1, new VmSchedulerTimeShared(peList1)));
        hostList.add(new Host(1, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw),
                storage, peList2, new VmSchedulerTimeShared(peList2)));

        // Define datacenter characteristics
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

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList),
                    storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    /**
     * Prints the list of executed cloudlets along with their execution details.
     * 
     * @param list list of cloudlets to be printed
     */
    private static void printCloudletList(List<Cloudlet> list) {
        DecimalFormat dft = new DecimalFormat("###.##");

        // Compute max field widths for formatting
        int maxIdWidth = "Cloudlet ID".length();
        int maxStatusWidth = "STATUS".length();
        int maxDcIdWidth = "Data center ID".length();
        int maxVmIdWidth = "VM ID".length();
        int maxTimeWidth = "Time".length();
        int maxStartWidth = "Start Time".length();
        int maxFinishWidth = "Finish Time".length();

        for (Cloudlet c : list) {
            maxIdWidth = Math.max(maxIdWidth, String.valueOf(c.getCloudletId()).length());
            maxStatusWidth = Math.max(maxStatusWidth, c.getStatus().name().length());
            maxDcIdWidth = Math.max(maxDcIdWidth, String.valueOf(c.getResourceId()).length());
            maxVmIdWidth = Math.max(maxVmIdWidth, String.valueOf(c.getGuestId()).length());
            maxTimeWidth = Math.max(maxTimeWidth, dft.format(c.getActualCPUTime()).length());
            maxStartWidth = Math.max(maxStartWidth, dft.format(c.getExecStartTime()).length());
            maxFinishWidth = Math.max(maxFinishWidth, dft.format(c.getExecFinishTime()).length());
        }

        // Build format string and print headers
        String fmtHeader = String.format("%%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds\n",
                maxIdWidth, maxStatusWidth, maxDcIdWidth, maxVmIdWidth, maxTimeWidth, maxStartWidth, maxFinishWidth);
        String fmtRow = String.format("%%-%dd  %%-%ds  %%-%dd  %%-%dd  %%%d.2f  %%%d.2f  %%%d.2f\n",
                maxIdWidth, maxStatusWidth, maxDcIdWidth, maxVmIdWidth, maxTimeWidth, maxStartWidth, maxFinishWidth);

        Log.println();
        Log.println("================================= OUTPUT ==================================");
        Log.println(String.format(fmtHeader, "Cloudlet ID", "STATUS", "Data center ID", "VM ID", "Time", "Start Time",
                "Finish Time"));

        // Print each cloudlet
        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.println(String.format(fmtRow, c.getCloudletId(), c.getStatus().name(), c.getResourceId(),
                        c.getGuestId(), c.getActualCPUTime(), c.getExecStartTime(), c.getExecFinishTime()));
            }
        }
    }
}

