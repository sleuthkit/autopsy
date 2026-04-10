rule Test1
{
    strings: 
        $defend_1116 = "Microsoft-Windows-Windows Defender/1116" nocase
		$defend_1117 = "Microsoft-Windows-Windows Defender/1117" nocase

    condition:
        $defend_1116 or $defend_1117
}

rule Test2
{
    strings: 
        $str = "NUMBER" nocase

    condition:
        $str
}

rule Test3
{
	meta:
	  description = "boink"
	  author = "Yo Mama"
	  
    strings: 
        $str1 = "onedrive" nocase
		$str2 = "vmware" nocase

    condition:
        $str1 or $str2
}


