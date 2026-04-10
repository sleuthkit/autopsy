import "pe"

rule Encoding
{
	meta:
	  author : "H. Carvey"
	  date : "2023-08-14"
	  reference : "https://www.elastic.co/guide/en/security/current/encoded-executable-stored-in-the-registry.html"
	
	strings:
	  $str1 = "TVqQAAMAAAAEAAAA*"
	
	condition:
	  $str1
	
}	

rule Executable
{
	meta:
	  author : "H. Carvey"
	  date : "2023-08-14"
	  reference : "https://dmfrsecurity.com/2021/12/21/100-days-of-yara-day-2-identifying-pe-files-and-measuring-speed-of-rules/"
	  
	strings:
	  $str1 = "MZ"
	  $str2 =  { 4D 5A }
	   
	condition:
      ($str1 or $str2) at 0 or uint16(0) == 0x5a4d
}












