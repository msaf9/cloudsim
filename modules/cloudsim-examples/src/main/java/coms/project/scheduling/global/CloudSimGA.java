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
 * Main class for executing a genetic algorithm (GA)-based scheduling
 * simulation using CloudSim.
 */
public class CloudSimGA {
	public static DatacenterBroker broker;
	private static List<Cloudlet> cloudletList;
	private static List<Vm> vmlist;

	/**
	 * Entry point of the simulation.
	 *
	 * @param args command line arguments (not used)
	 */
	public static void main(String[] args) {
		Log.println("Starting CloudSimGA...");

		try {
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance(); // simulation start time
			boolean trace_flag = false; // trace events

			// Initialize CloudSim
			CloudSim.init(num_user, calendar, trace_flag);

			// Create two datacenters
			Datacenter dc0 = createDatacenter("Datacenter_0");
			Datacenter dc1 = createDatacenter("Datacenter_1");

			// Create a broker
			broker = new DatacenterBroker("Broker");
			int brokerId = broker.getId();

			// Create virtual machines and cloudlets
			vmlist = createVM(brokerId, 20); // create 20 VMs
			cloudletList = createCloudlet(brokerId, 40); // create 40 Cloudlets

			// Apply genetic algorithm to find optimal scheduling
			int populationSize = 50;
			int generations = 100;
			int[] bestMapping = GeneticAlgorithm.schedule(cloudletList, vmlist, populationSize, generations);

			// Assign cloudlets to VMs based on best solution
			for (int i = 0; i < cloudletList.size(); i++) {
				cloudletList.get(i).setGuestId(bestMapping[i]);
			}

			broker.submitGuestList(vmlist);
			broker.submitCloudletList(cloudletList);

			// Start simulation
			CloudSim.startSimulation();
			CloudSim.stopSimulation();

			// Print simulation results
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			printCloudletList(newList);

			Log.println("CloudSimGA finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.println("Simulation terminated due to an unexpected error");
		}
	}

	/**
	 * Helper class for scheduling using a genetic algorithm.
	 */
	public static class GeneticAlgorithm {
		private static final Random rand = new Random();

		/**
		 * Run the genetic algorithm to find the best cloudlet-to-VM mapping.
		 *
		 * @param cloudlets    list of cloudlets
		 * @param vms          list of virtual machines
		 * @param popSize      size of the population
		 * @param generations  number of generations to evolve
		 * @return best chromosome (mapping of cloudlets to VMs)
		 */
		public static int[] schedule(List<Cloudlet> cloudlets, List<Vm> vms, int popSize, int generations) {
			int numCloudlets = cloudlets.size();
			int numVMs = vms.size();

			// Initialize population
			List<int[]> population = new ArrayList<>(popSize);
			for (int i = 0; i < popSize; i++) {
				int[] individual = new int[numCloudlets];
				for (int g = 0; g < numCloudlets; g++) {
					individual[g] = rand.nextInt(numVMs);
				}
				population.add(individual);
			}

			int[] best = null;
			double bestFit = Double.NEGATIVE_INFINITY;

			// Evolution loop
			for (int gen = 0; gen < generations; gen++) {
				double[] fitness = new double[popSize];
				for (int i = 0; i < popSize; i++) {
					fitness[i] = fitness(population.get(i), cloudlets, vms);
					if (fitness[i] > bestFit) {
						bestFit = fitness[i];
						best = population.get(i).clone();
					}
				}

				// Create new population
				List<int[]> newPop = new ArrayList<>(popSize);
				while (newPop.size() < popSize) {
					int[] parent1 = tournamentSelect(population, fitness);
					int[] parent2 = tournamentSelect(population, fitness);
					int[] child = crossover(parent1, parent2);
					mutate(child, numVMs);
					newPop.add(child);
				}
				population = newPop;
			}

			return best;
		}

		/**
		 * Compute fitness as the inverse of makespan.
		 */
		private static double fitness(int[] individual, List<Cloudlet> cloudlets, List<Vm> vms) {
			double[] vmLoad = new double[vms.size()];
			for (int i = 0; i < individual.length; i++) {
				int vmIndex = individual[i];
				double length = cloudlets.get(i).getCloudletLength();
				double mips = vms.get(vmIndex).getMips();
				vmLoad[vmIndex] += length / mips;
			}
			double makespan = 0;
			for (double load : vmLoad) {
				if (load > makespan)
					makespan = load;
			}
			return 1.0 / makespan;
		}

		/**
		 * Select an individual using binary tournament.
		 */
		private static int[] tournamentSelect(List<int[]> pop, double[] fit) {
			int i1 = rand.nextInt(pop.size()), i2 = rand.nextInt(pop.size());
			return (fit[i1] > fit[i2]) ? pop.get(i1) : pop.get(i2);
		}

		/**
		 * Single-point crossover between two parents.
		 */
		private static int[] crossover(int[] p1, int[] p2) {
			int len = p1.length;
			int[] child = new int[len];
			int cp = rand.nextInt(len);
			System.arraycopy(p1, 0, child, 0, cp);
			System.arraycopy(p2, cp, child, cp, len - cp);
			return child;
		}

		/**
		 * Mutate an individual with a low probability.
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
	 * Create a list of virtual machines.
	 *
	 * @param userId the ID of the user
	 * @param vms    number of VMs to create
	 * @return list of VMs
	 */
	private static List<Vm> createVM(int userId, final int vms) {
		List<Vm> list = new ArrayList<>();
		long size = 10000;
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
	 * Create a list of cloudlets.
	 *
	 * @param userId    the ID of the user
	 * @param cloudlets number of cloudlets to create
	 * @return list of cloudlets
	 */
	private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
		List<Cloudlet> list = new ArrayList<>();
		long length = 1000;
		long fileSize = 300;
		long outputSize = 300;
		int pesNumber = 1;
		UtilizationModel um = new UtilizationModelFull();

		for (int i = 0; i < cloudlets; i++) {
			Cloudlet c = new Cloudlet(i, length, pesNumber, fileSize, outputSize, um, um, um);
			c.setUserId(userId);
			list.add(c);
		}
		return list;
	}

	/**
	 * Create a datacenter with two hosts.
	 *
	 * @param name name of the datacenter
	 * @return Datacenter object
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

		int hostId = 0, ram = 2048;
		long storage = 1000000;
		int bw = 10000;
		hostList.add(new Host(hostId++, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList1,
				new VmSchedulerTimeShared(peList1)));
		hostList.add(new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList2,
				new VmSchedulerTimeShared(peList2)));

		String arch = "x86", os = "Linux", vmm = "Xen";
		double time_zone = 10.0, cost = 3.0, costPerMem = 0.05, costPerStorage = 0.1, costPerBw = 0.1;
		LinkedList<Storage> storageList = new LinkedList<>();
		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList, time_zone,
				cost, costPerMem, costPerStorage, costPerBw);

		try {
			return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Print formatted results of executed cloudlets.
	 *
	 * @param list list of completed cloudlets
	 */
	private static void printCloudletList(List<Cloudlet> list) {
		int maxIdWidth = "Cloudlet ID".length();
		int maxStatusWidth = "STATUS".length();
		int maxDcWidth = "Data center ID".length();
		int maxVmWidth = "VM ID".length();
		int maxTimeWidth = "Time".length();
		int maxStartWidth = "Start Time".length();
		int maxFinishWidth = "Finish Time".length();

		DecimalFormat dft = new DecimalFormat("###.##");

		for (Cloudlet cl : list) {
			maxIdWidth = Math.max(maxIdWidth, Integer.toString(cl.getCloudletId()).length());
			maxStatusWidth = Math.max(maxStatusWidth, cl.getStatus().toString().length());
			maxDcWidth = Math.max(maxDcWidth, Integer.toString(cl.getResourceId()).length());
			maxVmWidth = Math.max(maxVmWidth, Integer.toString(cl.getGuestId()).length());
			maxTimeWidth = Math.max(maxTimeWidth, dft.format(cl.getActualCPUTime()).length());
			maxStartWidth = Math.max(maxStartWidth, dft.format(cl.getExecStartTime()).length());
			maxFinishWidth = Math.max(maxFinishWidth, dft.format(cl.getExecFinishTime()).length());
		}

		String fmt = String.format("%%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds  %%-%ds%n", maxIdWidth,
				maxStatusWidth, maxDcWidth, maxVmWidth, maxTimeWidth, maxStartWidth, maxFinishWidth);

		Log.println();
		Log.println("================================= OUTPUT =================================");
		Log.println(String.format(fmt, "Cloudlet ID", "STATUS", "Data center ID", "VM ID", "Time", "Start Time",
				"Finish Time"));

		for (Cloudlet cl : list) {
			if (cl.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
				Log.println(String.format(fmt, cl.getCloudletId(), "SUCCESS", cl.getResourceId(), cl.getGuestId(),
						dft.format(cl.getActualCPUTime()), dft.format(cl.getExecStartTime()),
						dft.format(cl.getExecFinishTime())));
			}
		}
	}
	
}
