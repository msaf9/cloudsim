package coms.project.scheduling.local;

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
 * Demonstrates how to use CloudSim with a round-robin (time-shared) scheduling policy
 * to execute cloudlets with varying lengths across multiple VMs and datacenters.
 */
public class CloudSimRR {

	/** The broker used to manage VM and Cloudlet submissions. */
	public static DatacenterBroker broker;

	/** List to store all created Cloudlets. */
	private static List<Cloudlet> cloudletList;

	/** List to store all created VMs. */
	private static List<Vm> vmlist;

	/**
	 * Creates a list of VMs with a time-shared (round-robin) scheduler.
	 *
	 * @param userId ID of the broker/user creating the VMs.
	 * @param vms    Number of VMs to create.
	 * @return List of created VM instances.
	 */
	private static List<Vm> createVM(int userId, final int vms) {
		List<Vm> list = new ArrayList<>();

		long size = 10000; // image size (MB)
		int ram = 512;     // VM memory (MB)
		int mips = 1000;
		long bw = 1000;
		int pesNumber = 1; // number of CPUs
		String vmm = "Xen"; // Virtual Machine Monitor (hypervisor)

		for (int i = 0; i < vms; i++) {
			list.add(new Vm(i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared()));
		}

		return list;
	}

	/**
	 * Creates a list of Cloudlets with randomized lengths.
	 *
	 * @param userId   ID of the broker/user submitting the Cloudlets.
	 * @param cloudlets Number of Cloudlets to create.
	 * @return List of created Cloudlets.
	 */
	private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
		List<Cloudlet> list = new ArrayList<>();

		long fileSize = 300;
		long outputSize = 300;
		int pesNumber = 1;
		UtilizationModel utilizationModel = new UtilizationModelFull();

		long minLength = 500;  // Minimum length in MI
		long maxLength = 2000; // Maximum length in MI
		Random rand = new Random(12345);

		for (int i = 0; i < cloudlets; i++) {
			long length = minLength + rand.nextInt((int) (maxLength - minLength + 1));
			Cloudlet cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilizationModel,
					utilizationModel, utilizationModel);
			cloudlet.setUserId(userId);
			list.add(cloudlet);
		}

		return list;
	}

	/**
	 * Main method to run the CloudSim round-robin scheduling simulation.
	 *
	 * @param args not used.
	 */
	public static void main(String[] args) {
		Log.println("Starting CloudSimRR...");

		try {
			// Step 1: Initialize CloudSim
			int num_user = 1;
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false;

			CloudSim.init(num_user, calendar, trace_flag);

			// Step 2: Create Datacenters
			Datacenter datacenter0 = createDatacenter("Datacenter_0");
			Datacenter datacenter1 = createDatacenter("Datacenter_1");

			// Step 3: Create Broker
			broker = new DatacenterBroker("Broker");
			int brokerId = broker.getId();

			// Step 4: Create and submit VMs and Cloudlets
			vmlist = createVM(brokerId, 20);
			cloudletList = createCloudlet(brokerId, 40);

			broker.submitGuestList(vmlist);
			broker.submitCloudletList(cloudletList);

			// Step 5: Start Simulation
			CloudSim.startSimulation();

			// Step 6: Collect results
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			CloudSim.stopSimulation();

			printCloudletList(newList);
			Log.println("CloudSimRR finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.println("The simulation has been terminated due to an unexpected error");
		}
	}

	/**
	 * Creates a datacenter with two hosts, each configured with PEs and time-shared schedulers.
	 *
	 * @param name The name of the datacenter.
	 * @return The created Datacenter object.
	 */
	private static Datacenter createDatacenter(String name) {
		List<Host> hostList = new ArrayList<>();

		// Processing elements for hosts
		List<Pe> peList1 = new ArrayList<>();
		List<Pe> peList2 = new ArrayList<>();
		int mips = 1000;

		peList1.add(new Pe(0, new PeProvisionerSimple(mips)));
		peList1.add(new Pe(1, new PeProvisionerSimple(mips)));
		peList1.add(new Pe(2, new PeProvisionerSimple(mips)));
		peList1.add(new Pe(3, new PeProvisionerSimple(mips)));

		peList2.add(new Pe(0, new PeProvisionerSimple(mips)));
		peList2.add(new Pe(1, new PeProvisionerSimple(mips)));

		// Create Hosts
		int hostId = 0;
		int ram = 2048; // MB
		long storage = 1000000; // MB
		int bw = 10000;

		hostList.add(new Host(hostId++, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw),
				storage, peList1, new VmSchedulerTimeShared(peList1)));

		hostList.add(new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw),
				storage, peList2, new VmSchedulerTimeShared(peList2)));

		// Datacenter characteristics
		String arch = "x86"; 
		String os = "Linux"; 
		String vmm = "Xen";
		double time_zone = 10.0;
		double cost = 3.0;
		double costPerMem = 0.05;
		double costPerStorage = 0.1;
		double costPerBw = 0.1;
		LinkedList<Storage> storageList = new LinkedList<>();

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList,
				time_zone, cost, costPerMem, costPerStorage, costPerBw);

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
	 * Prints the results of executed Cloudlets, including their execution time,
	 * start and finish times, and wait time.
	 *
	 * @param list List of Cloudlets after simulation.
	 */
	private static void printCloudletList(List<Cloudlet> list) {
		DecimalFormat dft = new DecimalFormat("###.##");
		String format = "%-12s%-12s%-16s%-12s%-12s%-12s%-12s%-12s";

		Log.println();
		Log.println("============================================ OUTPUT ============================================");
		Log.println(String.format(format, "CloudletID", "STATUS", "DataCenterID", "VMID", "ExecTime", "StartTime",
				"FinishTime", "WaitTime"));

		for (Cloudlet cloudlet : list) {
			if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
				double exec = cloudlet.getActualCPUTime();
				double start = cloudlet.getExecStartTime();
				double finish = cloudlet.getExecFinishTime();
				double wait = finish - start - exec;

				Log.println(String.format(format, cloudlet.getCloudletId(), "SUCCESS", cloudlet.getResourceId(),
						cloudlet.getGuestId(), dft.format(exec), dft.format(start), dft.format(finish),
						dft.format(wait)));
			}
		}
	}
}

