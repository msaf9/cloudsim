package coms.project.scheduling.global;

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

/**
 * CloudSimMaxMin demonstrates the Max–Min scheduling algorithm using the CloudSim toolkit.
 * 
 * <p>This simulation initializes two datacenters, a set of VMs and cloudlets, and then schedules 
 * cloudlets using the Max–Min heuristic, which prioritizes cloudlets with the largest task lengths 
 * to the VM with the minimum expected completion time.</p>
 * 
 */
public class CloudSimMaxMin {
	public static DatacenterBroker broker;
	private static List<Cloudlet> cloudletList;
	private static List<Vm> vmlist;

	/**
	 * Creates a list of virtual machines (VMs) for the given user.
	 *
	 * @param userId the ID of the user to whom VMs belong
	 * @param vms the number of VMs to create
	 * @return a list of VMs
	 */
	private static List<Vm> createVM(int userId, final int vms) {
		List<Vm> list = new ArrayList<>();

		long size = 10000; // image size (MB)
		int ram = 512; // vm memory (MB)
		int mips = 1000;
		long bw = 1000;
		int pesNumber = 1; // number of cpus
		String vmm = "Xen";

		for (int i = 0; i < vms; i++) {
			list.add(new Vm(i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared()));
		}
		return list;
	}

	/**
	 * Creates a list of cloudlets (tasks) with random lengths.
	 * Cloudlet lengths range between 500 and 5000 million instructions.
	 *
	 * @param userId the ID of the user to whom cloudlets belong
	 * @param cloudlets the number of cloudlets to create
	 * @return a list of cloudlets
	 */
	private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
		List<Cloudlet> list = new ArrayList<>();
		Random rand = new Random(1234);

		long fileSize = 300;
		long outputSize = 300;
		int pesNumber = 1;
		UtilizationModel utilizationModel = new UtilizationModelFull();

		for (int i = 0; i < cloudlets; i++) {
			// generate length between 500 and 5000 MIs
			long length = 500 + rand.nextInt(4501);
			Cloudlet cl = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel,
					utilizationModel);
			cl.setUserId(userId);
			list.add(cl);
		}
		return list;
	}

	/**
	 * Applies the Max–Min scheduling heuristic.
	 * The largest cloudlet is always scheduled to the VM with the least total workload.
	 *
	 * @param broker the broker managing VM-cloudlet bindings
	 * @param cloudlets list of cloudlets to schedule
	 * @param vms list of available virtual machines
	 */
	private static void applyMaxMin(DatacenterBroker broker, List<Cloudlet> cloudlets, List<Vm> vms) {
		Map<Integer, Double> vmCompletionTime = new HashMap<>();
		for (Vm vm : vms) {
			vmCompletionTime.put(vm.getId(), 0.0);
		}

		List<Cloudlet> unsettled = new ArrayList<>(cloudlets);
		while (!unsettled.isEmpty()) {
			Cloudlet maxCl = Collections.max(unsettled, Comparator.comparingLong(Cloudlet::getCloudletLength));
			Vm targetVm = Collections.min(vms, Comparator.comparing(vm -> vmCompletionTime.get(vm.getId())));
			double execTime = (double) maxCl.getCloudletLength() / targetVm.getMips();
			broker.bindCloudletToVm(maxCl.getCloudletId(), targetVm.getId());
			vmCompletionTime.put(targetVm.getId(), vmCompletionTime.get(targetVm.getId()) + execTime);
			unsettled.remove(maxCl);
		}
	}

	/**
	 * Main method for running the simulation.
	 * Initializes the simulation environment, creates datacenters, VMs, and cloudlets,
	 * applies Max–Min scheduling, and prints results.
	 *
	 * @param args command-line arguments (not used)
	 */
	public static void main(String[] args) {
		Log.println("Starting CloudSimMaxMin...");

		try {
			int numUser = 1;
			Calendar calendar = Calendar.getInstance();
			boolean traceFlag = false;

			CloudSim.init(numUser, calendar, traceFlag);

			Datacenter datacenter0 = createDatacenter("Datacenter_0");
			Datacenter datacenter1 = createDatacenter("Datacenter_1");

			broker = new DatacenterBroker("Broker");
			int brokerId = broker.getId();

			vmlist = createVM(brokerId, 20);
			cloudletList = createCloudlet(brokerId, 40);

			broker.submitGuestList(vmlist);
			broker.submitCloudletList(cloudletList);

			// Apply the Max–Min scheduling heuristic
			applyMaxMin(broker, cloudletList, vmlist);

			CloudSim.startSimulation();
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			CloudSim.stopSimulation();

			printCloudletList(newList);
			Log.println("CloudSimMaxMin finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.println("The simulation has been terminated due to an unexpected error");
		}
	}

	/**
	 * Creates a datacenter with two hosts.
	 *
	 * @param name the name of the datacenter
	 * @return a Datacenter object
	 */
	private static Datacenter createDatacenter(String name) {
		List<Host> hostList = new ArrayList<>();

		List<Pe> peList1 = new ArrayList<>();
		int mips = 1000;
		for (int i = 0; i < 4; i++) {
			peList1.add(new Pe(i, new PeProvisionerSimple(mips)));
		}

		List<Pe> peList2 = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			peList2.add(new Pe(i, new PeProvisionerSimple(mips)));
		}

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
		double timeZone = 10.0;
		double cost = 3.0;
		double costPerMem = 0.05;
		double costPerStorage = 0.1;
		double costPerBw = 0.1;
		LinkedList<Storage> storageList = new LinkedList<>();

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList, timeZone,
				cost, costPerMem, costPerStorage, costPerBw);

		try {
			return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Prints the list of completed cloudlets along with their execution details
	 * in a formatted table layout.
	 *
	 * @param list the list of cloudlets whose results are to be printed
	 */
	private static void printCloudletList(List<Cloudlet> list) {
		String[] headers = { "Cloudlet ID", "STATUS", "Data center ID", "VM ID", "Time", "Start Time", "Finish Time" };
		List<String[]> rows = new ArrayList<>(list.size());
		DecimalFormat dft = new DecimalFormat("###.##");

		int cols = headers.length;
		int[] maxWidths = new int[cols];
		for (int i = 0; i < cols; i++) {
			maxWidths[i] = headers[i].length();
		}

		for (Cloudlet cl : list) {
			String status = (cl.getStatus() == Cloudlet.CloudletStatus.SUCCESS ? "SUCCESS" : cl.getStatus().toString());
			String[] row = new String[] { String.valueOf(cl.getCloudletId()), status,
					String.valueOf(cl.getResourceId()), String.valueOf(cl.getGuestId()),
					dft.format(cl.getActualCPUTime()), dft.format(cl.getExecStartTime()),
					dft.format(cl.getExecFinishTime()) };
			rows.add(row);
			for (int i = 0; i < cols; i++) {
				if (row[i].length() > maxWidths[i]) {
					maxWidths[i] = row[i].length();
				}
			}
		}

		// build format string
		StringBuilder fmt = new StringBuilder("  ");
		for (int w : maxWidths) {
			fmt.append("%-").append(w + 2).append("s");
		}
		fmt.append("\n");

		Log.println();
		Log.println("================================== OUTPUT ==================================");
		Log.print(String.format(fmt.toString(), (Object[]) headers));
		for (String[] row : rows) {
			Log.print(String.format(fmt.toString(), (Object[]) row));
		}
	}
}
