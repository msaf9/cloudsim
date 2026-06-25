package coms.project.scheduling;

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
 * CloudSimFCFS demonstrates the use of the CloudSim framework with a
 * First-Come, First-Serve (FCFS) scheduling strategy for cloudlets
 * using a SpaceShared scheduler.
 *
 * It sets up two datacenters, creates VMs and cloudlets, and simulates
 * their execution, printing the results at the end.
 *
 * The goal is to show how cloudlets are scheduled and executed using FCFS.
 */
public class CloudSimFCFS {
    
    /** The broker used to submit VMs and Cloudlets. */
    public static DatacenterBroker broker;

    /** List of all Cloudlets created for the simulation. */
    private static List<Cloudlet> cloudletList;

    /** List of all VMs created for the simulation. */
    private static List<Vm> vmlist;

    /**
     * Creates a list of VMs to be submitted to the broker.
     *
     * @param userId the ID of the broker (user)
     * @param vms    the number of VMs to create
     * @return the list of VMs created
     */
    private static List<Vm> createVM(int userId, final int vms) {
        List<Vm> list = new ArrayList<>();

        long size = 10000; // image size (MB)
        int ram = 512; // VM memory (MB)
        int mips = 1000;
        long bw = 1000;
        int pesNumber = 1; // number of CPUs
        String vmm = "Xen"; // Virtual Machine Monitor

        for (int i = 0; i < vms; i++) {
            list.add(new Vm(i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared()));
        }

        return list;
    }

    /**
     * Creates a list of Cloudlets to be submitted to the broker.
     *
     * @param userId    the ID of the broker (user)
     * @param cloudlets the number of Cloudlets to create
     * @return the list of Cloudlets created
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();

        long length = 1000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < cloudlets; i++) {
            Cloudlet cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilizationModel,
                    utilizationModel, utilizationModel);
            cloudlet.setUserId(userId);
            list.add(cloudlet);
        }

        return list;
    }

    /**
     * Main method which sets up the simulation environment and starts the simulation.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimFCFS...");

        try {
            // Step 1: Initialize the CloudSim package
            int num_user = 1; // number of users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            // Step 2: Create Datacenters
            Datacenter datacenter0 = createDatacenter("Datacenter_0");
            Datacenter datacenter1 = createDatacenter("Datacenter_1");

            // Step 3: Create Broker
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // Step 4: Create and Submit VMs and Cloudlets
            vmlist = createVM(brokerId, 20); // creating 20 VMs
            cloudletList = createCloudlet(brokerId, 40); // creating 40 Cloudlets

            broker.submitGuestList(vmlist);
            broker.submitCloudletList(cloudletList);

            // Step 5: Start Simulation
            CloudSim.startSimulation();

            // Final Step: Retrieve and display results
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            printCloudletList(newList);

            Log.println("CloudSimFCFS finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("The simulation has been terminated due to an unexpected error");
        }
    }

    /**
     * Creates a Datacenter with specified name and configured hosts.
     *
     * @param name the name of the Datacenter
     * @return the Datacenter object created
     */
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();

        List<Pe> peList1 = new ArrayList<>();
        List<Pe> peList2 = new ArrayList<>();
        int mips = 1000;

        peList1.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList1.add(new Pe(1, new PeProvisionerSimple(mips)));
        peList1.add(new Pe(2, new PeProvisionerSimple(mips)));
        peList1.add(new Pe(3, new PeProvisionerSimple(mips)));

        peList2.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList2.add(new Pe(1, new PeProvisionerSimple(mips)));

        int hostId = 0;
        int ram = 2048; // MB
        long storage = 1000000; // MB
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

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    /**
     * Prints the details of each Cloudlet after simulation execution.
     *
     * @param list the list of Cloudlets to print
     */
    private static void printCloudletList(List<Cloudlet> list) {
        DecimalFormat dft = new DecimalFormat("###.##");

        String format = "%-12s%-12s%-16s%-10s%-12s%-15s%-15s";

        Log.println();
        Log.println("======================================== OUTPUT ========================================");
        Log.println(String.format(format, "Cloudlet ID", "STATUS", "Data center ID", "VM ID", "Time", "Start Time",
                "Finish Time"));

        for (Cloudlet cloudlet : list) {
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.println(String.format(format, cloudlet.getCloudletId(), "SUCCESS", cloudlet.getResourceId(),
                        cloudlet.getGuestId(), dft.format(cloudlet.getActualCPUTime()),
                        dft.format(cloudlet.getExecStartTime()), dft.format(cloudlet.getExecFinishTime())));
            }
        }
    }
}
