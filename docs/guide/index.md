# LVH Yard

Reach any development service on your HomeLab by name, not by IP and port. LVH Yard gives each service that you run on your computers a hostname such as `http://grafana.lvh.me`. The hostname works on your own computer and on every other computer in your Yard.

## Problems it removes

- "I forget which port each service uses."
- "Docker gives my container a new port after every `compose up`."
- "I edit `/etc/hosts` on each computer, and I do it again for the next one."
- "`localhost:3000` breaks my cookies and my OAuth callbacks."
- "I need HTTPS in development, and I do not want to click through warnings."
- "My teammate cannot open my service."

## Start here

1. [What LVH Yard solves](start/what-it-solves.md) answers each problem and lists what LVH Yard does not do.
2. [Install LVH Yard](start/install.md) installs the executable and starts a Workstation.
3. [Pick your Base Domain](start/pick-your-base-domain.md) explains the domain that ends every hostname.
4. [Your first Service](start/first-service.md) gives a process and a Docker container a hostname.
