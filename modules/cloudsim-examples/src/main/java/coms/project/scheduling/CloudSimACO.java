package coms.project.scheduling;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * This class implements an Ant Colony Optimization (ACO) based scheduling
 * algorithm for cloudlet-to-VM assignment using the CloudSim toolkit.
 */
public class CloudSimACO {

    /** Broker to manage VMs and cloudlets */
    public static DatacenterBroker broker;

    /** List of cloudlets to be scheduled */
    private static List<Cloudlet> cloudletList;

    /** List of virtual machines */
    private static List<Vm> vmlist;

    /**
     * Creates a list of VMs.
     *
     * @param userId ID of the broker/user
     * @param vms    number of VMs to create
     * @return list of VMs
     */
    private static List<Vm> createVM(int userId, final int vms) {
        List<Vm> list = new ArrayList<>();
        long size = 10000; // image size (MB)
        int ram = 512; // vm memory (MB)
        int mips = 1000;
        long bw = 1000;
        int pesNumber = 1; // number of cpus
        String vmm = "Xen"; // VMM name

        for (int i = 0; i < vms; i++) {
            list.add(new Vm(i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared()));
        }
        return list;
    }

    /**
     * Creates a list of Cloudlets.
     *
     * @param userId    ID of the broker/user
     * @param cloudlets number of cloudlets to create
     * @return list of Cloudlets
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        List<Cloudlet> list = new ArrayList<>();
        long length = 1000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < cloudlets; i++) {
            Cloudlet cl = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel,
                    utilizationModel);
            cl.setUserId(userId);
            list.add(cl);
        }
        return list;
    }

    /**
     * Main method to run the ACO scheduling simulation.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimACO...");

        try {
            // Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // Create two datacenters
            Datacenter datacenter0 = createDatacenter("Datacenter_0");
            Datacenter datacenter1 = createDatacenter("Datacenter_1");

            // Create a broker
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // Create VMs and Cloudlets
            vmlist = createVM(brokerId, 20);
            cloudletList = createCloudlet(brokerId, 40);

            // ACO algorithm parameters
            int numAnts = 30;
            int maxIter = 50;
            double alpha = 1.0; // pheromone importance
            double beta = 2.0; // heuristic importance
            double evap = 0.5; // evaporation rate
            double Q = 500.0; // pheromone deposit factor

            int C = cloudletList.size();
            int V = vmlist.size();

            // Initialize pheromone matrix
            double[][] pheromone = new double[C][V];
            for (int i = 0; i < C; i++)
                for (int j = 0; j < V; j++)
                    pheromone[i][j] = 1.0;

            // Compute heuristic matrix (inverse execution time)
            double[][] heuristic = new double[C][V];
            for (int i = 0; i < C; i++) {
                long length = cloudletList.get(i).getCloudletLength();
                for (int j = 0; j < V; j++) {
                    double mips = vmlist.get(j).getMips();
                    heuristic[i][j] = 1.0 / (length / mips);
                }
            }

            // ACO optimization loop
            Random rand = new Random();
            int[] bestAssign = new int[C];
            double bestMakespan = Double.MAX_VALUE;

            for (int iter = 0; iter < maxIter; iter++) {
                List<int[]> solList = new ArrayList<>();
                List<Double> makespans = new ArrayList<>();

                for (int ant = 0; ant < numAnts; ant++) {
                    int[] assign = new int[C];
                    double[] load = new double[V];

                    // Construct solution probabilistically
                    for (int i = 0; i < C; i++) {
                        double sum = 0.0;
                        double[] probs = new double[V];
                        for (int j = 0; j < V; j++) {
                            probs[j] = Math.pow(pheromone[i][j], alpha) * Math.pow(heuristic[i][j], beta);
                            sum += probs[j];
                        }
                        double pick = rand.nextDouble() * sum;
                        double cum = 0.0;
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

                    // Calculate makespan
                    double makespan = 0.0;
                    for (double l : load)
                        makespan = Math.max(makespan, l);

                    solList.add(assign);
                    makespans.add(makespan);

                    // Update best solution
                    if (makespan < bestMakespan) {
                        bestMakespan = makespan;
                        bestAssign = assign.clone();
                    }
                }

                // Pheromone evaporation
                for (int i = 0; i < C; i++)
                    for (int j = 0; j < V; j++)
                        pheromone[i][j] *= (1.0 - evap);

                // Pheromone deposit
                for (int a = 0; a < numAnts; a++) {
                    double contrib = Q / makespans.get(a);
                    int[] sol = solList.get(a);
                    for (int i = 0; i < C; i++) {
                        pheromone[i][sol[i]] += contrib;
                    }
                }
            }

            // Assign cloudlets to VMs based on best ACO solution
            for (int i = 0; i < C; i++) {
                cloudletList.get(i).setGuestId(vmlist.get(bestAssign[i]).getId());
            }

            // Submit VMs and cloudlets to the broker
            broker.submitGuestList(vmlist);
            broker.submitCloudletList(cloudletList);

            // Start simulation
            CloudSim.startSimulation();
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // Print results
            printCloudletList(newList);
            Log.println("CloudSimACO finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("The simulation has been terminated due to an unexpected error");
        }
    }

    /**
     * Creates a datacenter with two hosts and a given name.
     *
     * @param name Name of the datacenter
     * @return Datacenter instance
     */
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();

        // Processing Elements (PEs) for each host
        List<Pe> peList1 = new ArrayList<>();
        int mips = 1000;
        peList1.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList1.add(new Pe(1, new PeProvisionerSimple(mips)));
        peList1.add(new Pe(2, new PeProvisionerSimple(mips)));
        peList1.add(new Pe(3, new PeProvisionerSimple(mips)));

        List<Pe> peList2 = new ArrayList<>();
        peList2.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList2.add(new Pe(1, new PeProvisionerSimple(mips)));

        // Host configuration
        int hostId = 0;
        int ram = 2048;
        long storage = 1000000;
        int bw = 10000;

        hostList.add(new Host(hostId++, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList1,
                new VmSchedulerTimeShared(peList1)));
        hostList.add(new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList2,
                new VmSchedulerTimeShared(peList2)));

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
     * Prints a formatted list of Cloudlet results.
     *
     * @param list List of Cloudlets after simulation
     */
    private static void printCloudletList(List<Cloudlet> list) {
        Log.println();
        Log.println("========== OUTPUT ==========");

        // Prepare values for each row
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Cloudlet ID", "STATUS", "Data center ID", "VM ID", "Time", "Start Time",
                "Finish Time" });

        DecimalFormat dft = new DecimalFormat("###.##");
        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                rows.add(new String[] { String.valueOf(c.getCloudletId()), "SUCCESS", String.valueOf(c.getResourceId()),
                        String.valueOf(c.getGuestId()), dft.format(c.getActualCPUTime()),
                        dft.format(c.getExecStartTime()), dft.format(c.getExecFinishTime()) });
            }
        }

        // Compute max width of each column
        int cols = rows.get(0).length;
        int[] widths = new int[cols];
        for (String[] row : rows) {
            for (int i = 0; i < cols; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        // Build format string
        StringBuilder fmt = new StringBuilder();
        for (int w : widths) {
            fmt.append(" %-" + w + "s ");
        }
        String formatLine = fmt.toString().trim();

        // Print header
        String[] header = rows.get(0);
        Log.println(String.format(formatLine, (Object[]) header));

        // Print separator
        StringBuilder sep = new StringBuilder();
        for (int w : widths) {
            sep.append(" ");
            for (int i = 0; i < w; i++)
                sep.append("-");
            sep.append(" ");
        }
        Log.println(sep.toString().trim());

        // Print data rows
        for (int i = 1; i < rows.size(); i++) {
            Log.println(String.format(formatLine, (Object[]) rows.get(i)));
        }
    }

}
