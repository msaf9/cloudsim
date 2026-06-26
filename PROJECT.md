# 

## Introduction

In cloud computing (and specifically in CloudSim framework), "scheduling" actually refers to broker's scheduling and VM processing.

Scheduling in the cloud is a two-level hierarcy.

1. Global Scheduling (The Broker's Job)
    > Also called as Task Scheduling, Resource Provisioning, or Load Balancing.

    - What it does? It decides which task (cloudlet) goes to which Virtual Machine (VM).
    - The Goal: Optimize things on a macro level - like minimizing the total time to finish all tasks (makespan), reducing energy comsumption of the data center, or keeping costs low for the user.
    - Examples: advanced AI algorithms like Genetic Algorithms or Ant Colony Optimization, Max-Min Heuristic, Particle Swarm Optimization, Whale Optimization Algorithm
    - In CloudSim, `DatacenterBroker` handles this global scheduling.

2. Local Scheduling (The VM's Job)
    > Known as CPU Scheduling or Internal VM Processing.

    - What it does? Once the Broker has dumped a bunch of tasks onto a single VM, the VM has to decide how to share its limited CPU cores (Processing Elements - PEs) among those tasks.
    - The Goal: Optimize things on a micro level - ensuring fairness, preventing tasks from timing out, and managing the queue inside the virtual operating system.
    - Examples: 
        - * Space-Shared (FCFS): Put the tasks in a line. Finish Task A completely before starting Task B.
        - Round-Robin/Time-Shared (Multitasking): Give Task A a fraction of a second of CPU time, then Task B a fraction, then back to Task A, running them all simultaneously but slower.
    - `CloudletScheduler` handles this local scheduling in CloudSim.

