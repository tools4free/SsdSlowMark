wmic diskdrive list brief /format:list > wmic-diskdrive.txt

wmic logicaldisk get /format:list > wmic-logicaldisk.txt

wmic partition get /format:list > wmic-partition.txt

wmic partition assoc /resultclass:win32_logicaldisk > wmic-partition-assoc.txt

wmic baseboard get /format:list > motherboard.txt

wmic cpu get Name /format:list > cpu.txt