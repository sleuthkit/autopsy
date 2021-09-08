#-----------------------------------------------------------
# shellitems.pl
# Perl script to parse shell items; access via 'require' pragma
#
# History:
#   20130923 - updated printData() code with (what I hope is) more stable 
#              code
#   20130522 - created 
#
# References
#  Andrew's Python code for Registry Decoder
#    http://code.google.com/p/registrydecoder/source/browse/trunk/templates/template_files/ShellBagMRU.py
#  Joachim Metz's shell item format specification
#    http://download.polytechnic.edu.na/pub4/download.sourceforge.net/pub/
#      sourceforge/l/project/li/liblnk/Documentation/Windows%20Shell%20Item%20format/
#      Windows%20Shell%20Item%20format.pdf
#  Converting DOS Date format
#    http://msdn.microsoft.com/en-us/library/windows/desktop/ms724274(v=VS.85).aspx
#
# Thanks to Willi Ballenthin and Joachim Metz for the documentation they 
# provided, Andrew Case for posting the Registry Decoder code, and Kevin 
# Moore for writing the shell bag parser for Registry Decoder, as well as 
# assistance with some parsing.
#
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
use Time::Local;
use Encode::Unicode;

my %guids = ("{bb64f8a7-bee7-4e1a-ab8d-7d8273f7fdb6}" => "Action Center",
    "{7a979262-40ce-46ff-aeee-7884ac3b6136}" => "Add Hardware",
    "{d20ea4e1-3957-11d2-a40b-0c5020524153}" => "Administrative Tools",
    "{c57a6066-66a3-4d91-9eb9-41532179f0a5}" => "AppSuggestedLocations",
    "{9c60de1e-e5fc-40f4-a487-460851a8d915}" => "AutoPlay",
    "{b98a2bea-7d42-4558-8bd1-832f41bac6fd}" => "Backup and Restore Center",
    "{0142e4d0-fb7a-11dc-ba4a-000ffe7ab428}" => "Biometric Devices",
    "{d9ef8727-cac2-4e60-809e-86f80a666c91}" => "BitLocker Drive Encryption",
    "{56784854-c6cb-462b-8169-88e350acb882}" => "Contacts",
    "{26ee0668-a00a-44d7-9371-beb064c98683}" => "Control Panel (Cat. View)",
    "{b2c761c6-29bc-4f19-9251-e6195265baf1}" => "Color Management",
    "{1206f5f1-0569-412c-8fec-3204630dfb70}" => "Credential Manager",
    "{e2e7934b-dce5-43c4-9576-7fe4f75e7480}" => "Date and Time",
    "{00c6d95f-329c-409a-81d7-c46c66ea7f33}" => "Default Location",
    "{17cd9488-1228-4b2f-88ce-4298e93e0966}" => "Default Programs",
    "{37efd44d-ef8d-41b1-940d-96973a50e9e0}" => "Desktop Gadgets",
    "{74246bfc-4c96-11d0-abef-0020af6b0b7a}" => "Device Manager",
    "{a8a91a66-3a7d-4424-8d24-04e180695c7a}" => "Devices and Printers",
    "{c555438b-3c23-4769-a71f-b6d3d9b6053a}" => "Display",
    "{d555645e-d4f8-4c29-a827-d93c859c4f2a}" => "Ease of Access Center",
    "{1777f761-68ad-4d8a-87bd-30b759fa33dd}" => "Favorites",
    "{323ca680-c24d-4099-b94d-446dd2d7249e}" => "Favorites",
    "{6dfd7c5c-2451-11d3-a299-00c04f8ef6af}" => "Folder Options",
    "{93412589-74d4-4e4e-ad0e-e0cb621440fd}" => "Fonts",
    "{259ef4b1-e6c9-4176-b574-481532c9bce8}" => "Game Controllers",
    "{15eae92e-f17a-4431-9f28-805e482dafd4}" => "Get Programs",
    "{cb1b7f8c-c50a-4176-b604-9e24dee8d4d1}" => "Getting Started",
    "{67ca7650-96e6-4fdd-bb43-a8e774f73a57}" => "HomeGroup",
    "{b4fb3f98-c1ea-428d-a78a-d1f5659cba93}" => "HomeGroup",
    "{87d66a43-7b11-4a28-9811-c86ee395acf7}" => "Indexing Options",
    "{a0275511-0e86-4eca-97c2-ecd8f1221d08}" => "Infrared",
    "{a3dd4f92-658a-410f-84fd-6fbbbef2fffe}" => "Internet Options",
    "{a304259d-52b8-4526-8b1a-a1d6cecc8243}" => "iSCSI Initiator",
    "{725be8f7-668e-4c7b-8f90-46bdb0936430}" => "Keyboard",
    "{bfb9d5e0-c6a9-404c-b2b2-ae6db6af4968}" => "Links",
    "{e9950154-c418-419e-a90a-20c5287ae24b}" => "Location and Other Sensors",
    "{1fa9085f-25a2-489b-85d4-86326eedcd87}" => "Manage Wireless Networks",
    "{6c8eec18-8d75-41b2-a177-8831d59d2d50}" => "Mouse",
    "{2112ab0a-c86a-4ffe-a368-0de96e47012e}" => "Music Library",
    "{7007acc7-3202-11d1-aad2-00805fc1270e}" => "Network Connections",
    "{8e908fc9-becc-40f6-915b-f4ca0e70d03d}" => "Network and Sharing Center",
    "{05d7b0f4-2121-4eff-bf6b-ed3f69b894d9}" => "Notification Area Icons",
    "{d24f75aa-4f2b-4d07-a3c4-469b3d9030c4}" => "Offline Files",
    "{96ae8d84-a250-4520-95a5-a47a7e3c548b}" => "Parental Controls",
    "{f82df8f7-8b9f-442e-a48c-818ea735ff9b}" => "Pen and Input Devices",
    "{5224f545-a443-4859-ba23-7b5a95bdc8ef}" => "People Near Me",
    "{78f3955e-3b90-4184-bd14-5397c15f1efc}" => "Performance Information and Tools",
    "{ed834ed6-4b5a-4bfe-8f11-a626dcb6a921}" => "Personalization",
    "{40419485-c444-4567-851a-2dd7bfa1684d}" => "Phone and Modem",
    "{f0d63f85-37ec-4097-b30d-61b4a8917118}" => "Photo Stream",
    "{025a5937-a6be-4686-a844-36fe4bec8b6d}" => "Power Options",
    "{2227a280-3aea-1069-a2de-08002b30309d}" => "Printers",
    "{fcfeecae-ee1b-4849-ae50-685dcf7717ec}" => "Problem Reports and Solutions",
    "{7b81be6a-ce2b-4676-a29e-eb907a5126c5}" => "Programs and Features",
    "{22877a6d-37a1-461a-91b0-dbda5aaebc99}" => "Recent Places",
    "{9fe63afd-59cf-4419-9775-abcc3849f861}" => "Recovery",
    "{62d8ed13-c9d0-4ce8-a914-47dd628fb1b0}" => "Regional and Language Options",
    "{241d7c96-f8bf-4f85-b01f-e2b043341a4b}" => "RemoteApp and Desktop Connections",
    "{4c5c32ff-bb9d-43b0-b5b4-2d72e54eaaa4}" => "Saved Games",
    "{7d1d3a04-debb-4115-95cf-2f29da2920da}" => "Saved Searches",
    "{00f2886f-cd64-4fc9-8ec5-30ef6cdbe8c3}" => "Scanners and Cameras",
    "{e211b736-43fd-11d1-9efb-0000f8757fcd}" => "Scanners and Cameras",
    "{d6277990-4c6a-11cf-8d87-00aa0060f5bf}" => "Scheduled Tasks",
    "{f2ddfc82-8f12-4cdd-b7dc-d4fe1425aa4d}" => "Sound",
    "{58e3c745-d971-4081-9034-86e34b30836a}" => "Speech Recognition Options",
    "{9c73f5e5-7ae7-4e32-a8e8-8d23b85255bf}" => "Sync Center",
    "{e413d040-6788-4c22-957e-175d1c513a34}" => "Sync Center Conflict Delegate Folder",
    "{bc48b32f-5910-47f5-8570-5074a8a5636a}" => "Sync Results Delegate Folder",
    "{f1390a9a-a3f4-4e5d-9c5f-98f3bd8d935c}" => "Sync Setup Delegate Folder",
    "{bb06c0e4-d293-4f75-8a90-cb05b6477eee}" => "System",
    "{80f3f1d5-feca-45f3-bc32-752c152e456e}" => "Tablet PC Settings",
    "{0df44eaa-ff21-4412-828e-260a8728e7f1}" => "Taskbar and Start Menu",
    "{d17d1d6d-cc3f-4815-8fe3-607e7d5d10b3}" => "Text to Speech",
    "{c58c4893-3be0-4b45-abb5-a63e4b8c8651}" => "Troubleshooting",
    "{60632754-c523-4b62-b45c-4172da012619}" => "User Accounts",
    "{be122a0e-4503-11da-8bde-f66bad1e3f3a}" => "Windows Anytime Upgrade",
    "{78cb147a-98ea-4aa6-b0df-c8681f69341c}" => "Windows CardSpace",
    "{d8559eb9-20c0-410e-beda-7ed416aecc2a}" => "Windows Defender",
    "{4026492f-2f69-46b8-b9bf-5654fc07e423}" => "Windows Firewall",
    "{3e7efb4c-faf1-453d-89eb-56026875ef90}" => "Windows Marketplace",
    "{5ea4f148-308c-46d7-98a9-49041b1dd468}" => "Windows Mobility Center",
    "{087da31b-0dd3-4537-8e23-64a18591f88b}" => "Windows Security Center",
    "{e95a4861-d57a-4be1-ad0f-35267e261739}" => "Windows SideShow",
    "{36eef7db-88ad-4e81-ad49-0e313f0c35f8}" => "Windows Update",
    "{724ef170-a42d-4fef-9f26-b60e846fba4f}" => "Administrative Tools",
    "{d0384e7d-bac3-4797-8f14-cba229b392b5}" => "Common Administrative Tools",
    "{de974d24-d9c6-4d3e-bf91-f4455120b917}" => "Common Files",
    "{c1bae2d0-10df-4334-bedd-7aa20b227a9d}" => "Common OEM Links",
    "{5399e694-6ce5-4d6c-8fce-1d8870fdcba0}" => "Control Panel",
    "{21ec2020-3aea-1069-a2dd-08002b30309d}" => "Control Panel",
    "{1ac14e77-02e7-4e5d-b744-2eb1ae5198b7}" => "CSIDL_SYSTEM",
    "{b4bfcc3a-db2c-424c-b029-7fe99a87c641}" => "Desktop",
    "{7b0db17d-9cd2-4a93-9733-46cc89022e7c}" => "Documents Library",
    "{fdd39ad0-238f-46af-adb4-6c85480369c7}" => "Documents",
    "{374de290-123f-4565-9164-39c4925e467b}" => "Downloads",
    "{de61d971-5ebc-4f02-a3a9-6c82895e5c04}" => "Get Programs",
    "{a305ce99-f527-492b-8b1a-7e76fa98d6e4}" => "Installed Updates",
    "{871c5380-42a0-1069-a2ea-08002b30309d}" => "Internet Explorer (Homepage)",
    "{031e4825-7b94-4dc3-b131-e946b44c8dd5}" => "Libraries",
    "{49bf5420-fa7f-11cf-8011-00a0c90a8f78}" => "Mobile Device",  #MS KB836152
    "{4bd8d571-6d19-48d3-be97-422220080e43}" => "Music",
    "{20d04fe0-3aea-1069-a2d8-08002b30309d}" => "My Computer",
    "{450d8fba-ad25-11d0-98a8-0800361b1103}" => "My Documents",
    "{fc9fb64a-1eb2-4ccf-af5e-1a497a9b5c2d}" => "My Shared Folders",
#    "{5e591a74-df96-48d3-8d67-1733bcee28ba}" => "My Documents",
    "{ed228fdf-9ea8-4870-83b1-96b02cfe0d52}" => "My Games",
    "{208d2c60-3aea-1069-a2d7-08002b30309d}" => "My Network Places",
    "{f02c1a0d-be21-4350-88b0-7367fc96ef3c}" => "Network", 
    "{33e28130-4e1e-4676-835a-98395c3bc3bb}" => "Pictures",
    "{a990ae9f-a03b-4e80-94bc-9912d7504104}" => "Pictures",
    "{7c5a40ef-a0fb-4bfc-874a-c0f2e0b9fa8e}" => "Program Files (x86)",
    "{905e63b6-c1bf-494e-b29c-65b732d3d21a}" => "Program Files",
    "{df7266ac-9274-4867-8d55-3bd661de872d}" => "Programs and Features",
    "{3214fab5-9757-4298-bb61-92a9deaa44ff}" => "Public Music",
    "{b6ebfb86-6907-413c-9af7-4fc2abf07cc5}" => "Public Pictures",
    "{2400183a-6185-49fb-a2d8-4a392a602ba3}" => "Public Videos",
    "{4336a54d-38b-4685-ab02-99bb52d3fb8b}"  => "Public",
    "{491e922f-5643-4af4-a7eb-4e7a138d8174}" => "Public",
    "{dfdf76a2-c82a-4d63-906a-5644ac457385}" => "Public",
    "{645ff040-5081-101b-9f08-00aa002f954e}" => "Recycle Bin",
    "{e17d4fc0-5564-11d1-83f2-00a0c90dc849}" => "Search Results",
    "{d65231b0-b2f1-4857-a4ce-a8e7c6ea7d27}" => "System32 (x86)",
    "{9e52ab10-f80d-49df-acb8-4330f5687855}" => "Temporary Burn Folder",
    "{f3ce0f7c-4901-4acc-8648-d5d44b04ef8f}" => "Users Files",
    "{59031a47-3f72-44a7-89c5-5595fe6b30ee}" => "User Files",
    "{59031a47-3f72-44a7-89c5-5595fe6b30ee}" => "Users",
    "{18989b1d-99b5-455b-841c-ab7c74e4ddfc}" => "Videos",
    "{f38bf404-1d43-42f2-9305-67de0b28fc23}" => "Windows");

#-----------------------------------------------------------
# parseVariableEntry()
# type = 0x00
#-----------------------------------------------------------
sub parseVariableEntry {
	my $data     = shift;
	my %item = ();
	
	$item{type} = unpack("C",substr($data,2,1));
	my $tag = unpack("C",substr($data,0x0A,1));
	
	if (unpack("v",substr($data,4,2)) == 0x1A) {
		my $guid = parseGUID(substr($data,14,16));
		
		if (exists $folder_types{$guid}) {
			$item{name} = $folder_types{$guid};
		}
		else {
			$item{name} = $guid;
		}
	}
	elsif (grep(/1SPS/,$data)) { 
	  my @seg = split(/1SPS/,$data);  
	  
	  my %segs = ();
	  foreach my $s (0..(scalar(@seg) - 1)) {
	  	my $guid = parseGUID(substr($seg[$s],0,16));
	  	$segs{$guid} = $seg[$s];
	  }
	  
	  if (exists $segs{"{b725f130-47ef-101a-a5f1-02608c9eebac}"}) { 
# Ref: http://msdn.microsoft.com/en-us/library/aa965725(v=vs.85).aspx	  	 	
	  	my $stuff = $segs{"{b725f130-47ef-101a-a5f1-02608c9eebac}"};

	  	my $tag = 1;
	  	my $cnt = 0x10;
	  	while($tag) {
	  		my $sz = unpack("V",substr($stuff,$cnt,4));
	  		my $id = unpack("V",substr($stuff,$cnt + 4,4));
#--------------------------------------------------------------
# sub-segment types
# 0x0a - file name
# 0x14 - short name
# 0x0e, 0x0f, 0x10 - mod date, create date, access date(?)
# 0x0c - size
#--------------------------------------------------------------	  	
	  		if ($sz == 0x00) {
	  			$tag = 0;
	  			next;
	  		}
	  		elsif ($id == 0x0a) {
	  			
	  			my $num = unpack("V",substr($stuff,$cnt + 13,4));
	  			my $str = substr($stuff,$cnt + 13 + 4,($num * 2));
	  			$str =~ s/\x00//g;
	  			$item{name} = $str;
	  		}
	  		$cnt += $sz;
	  	}
	  }
	  
#		if (exists $segs{"{5cbf2787-48cf-4208-b90e-ee5e5d420294}"}) {
#			my $stuff = $segs{"{5cbf2787-48cf-4208-b90e-ee5e5d420294}"};
#	  	my $tag = 1;
#	  	my $cnt = 0x10;
#	  	while($tag) {
#	  		my $sz = unpack("V",substr($stuff,$cnt,4));
#	  		my $id = unpack("V",substr($stuff,$cnt + 4,4));
#	  		
#	  		if ($sz == 0x00) {
#	  			$tag = 0;
#	  			next;
#	  		}
#	  		elsif ($id == 0x19) {
#	  			
#	  			my $num = unpack("V",substr($stuff,$cnt + 13,4));
#	  			my $str = substr($stuff,$cnt + 13 + 4,($num * 2));
#	  			$str =~ s/\x00//g;
#	  			$item{name} = $str;
#	  		}
#	  		$cnt += $sz;
#	  	}
#		}
	}
	elsif (substr($data,4,4) eq "AugM") {
		
		my @beef = split(/\xef\xbe/,$data);
		if (scalar (@beef) < 3) {
#			%item = parseFolderEntry($data);
      $item{name} = "Test";
		}
		else {
			my $temp = substr($beef[2],22,length($beef[2]) - 22);
			my $temp2 = substr($temp,12,length($temp) - 12);
			$item{name} = (split(/\x00\x00/,$temp2))[0];
			$item{name} =~ s/\x00//g;
		}
	
	}
# Following two entries are for Device Property data	
	elsif ($tag == 0x7b || $tag == 0xbb || $tag == 0xfb) {
		my ($sz1,$sz2,$sz3) = unpack("VVV",substr($data,0x3e,12));
		$item{name} = substr($data,0x4a,$sz1 * 2);
		$item{name} =~ s/\x00//g;
	}
	elsif ($tag == 0x02 || $tag == 0x03) {
		my ($sz1,$sz2,$sz3,$sz4) = unpack("VVVV",substr($data,0x26,16));
		$item{name} = substr($data,0x36,$sz1 * 2);
		$item{name} =~ s/\x00//g;
	}
	else {
		$item{name} = "Unknown Type";	
	}
	return %item;
}

#-----------------------------------------------------------
# parseNetworkEntry()
#
#-----------------------------------------------------------
sub parseNetworkEntry {
	my $data = shift;
	my %item = ();	
	$item{type} = unpack("C",substr($data,2,1));
	
	my @n = split(/\x00/,substr($data,5,length($data) - 5));
	$item{name} = $n[0];
	return %item;
}

#-----------------------------------------------------------
# parseZipSubFolderItem()
# parses what appears to be Zip file subfolders; this type 
# appears to contain the date and time of when the subfolder
# was accessed/opened, in string format.
#-----------------------------------------------------------
sub parseZipSubFolderItem {
	my $data     = shift;
	my %item = ();

# Get the opened/accessed date/time	
	$item{datetime} = substr($data,0x24,6);
	$item{datetime} =~ s/\x00//g;
	if ($item{datetime} eq "N/A") {
		
	}
	else {
		$item{datetime} = substr($data,0x24,40);
		$item{datetime} =~ s/\x00//g;
		my ($date,$time) = split(/\s+/,$item{datetime},2);
		my ($mon,$day,$yr) = split(/\//,$date,3);
		my ($hr,$min,$sec) = split(/:/,$time,3);

		my $gmtime = timegm($sec,$min,$hr,$day,($mon - 1),$yr);
		$item{datetime} = "$yr-$mon-$day $hr:$min:$sec";
#		::rptMsg("[Access_Time]: ".gmtime($gmtime));
	}
		
	my $sz = unpack("V",substr($data,0x54,4));
	my $sz2 = unpack("V",substr($data,0x58,4));
		
	my $str1 = substr($data,0x5C,$sz *2) if ($sz > 0);
	$str1 =~ s/\x00//g;
	
	if ($sz2 > 0) {
		my $str2 = substr($data,0x5C + ($sz * 2),$sz2 *2);
		$str2 =~ s/\x00//g;
		$item{name} = $str1."\\".$str2;
	}
	else {
		$item{name} = $str1;
	}
	return %item;
}

#-----------------------------------------------------------
# parse01ShellItem()
# I honestly have no idea what to do with this data; there's really
# no reference for or description of the format of this data.  For
# now, this is just a place holder
#-----------------------------------------------------------
sub parse01ShellItem {
	my $data = shift;
	my %item = ();
	$item{type} = unpack("C",substr($data,2,1));;
	$item{name} = "";
#	($item{val0},$item{val1}) = unpack("VV",substr($data,2,length($data) - 2));
	return %item;
}

#-----------------------------------------------------------
# parseXPShellDeviceItem()
# 
#-----------------------------------------------------------
sub parseXPShellDeviceItem {
	my $data = shift;
	my %item = ();
	my ($t0,$t1) = unpack("VV",substr($data,0x04,8));
	if ($t0 == 0 && $t1 == 0) {
		$item{timestamp} = 0;
	}
	else {
		$item{timestamp} = ::getTime($t0,$t1);
	}
# starting at offset 0x18, read the null-term. string as the name value
	my $str = substr($data,0x18,length($data) - 0x18);
	$item{name} = (split(/\x00/,$str))[0];
	return %item;
}

#-----------------------------------------------------------
# parseType64Item()
# Parses types 0x64, 0x65, 0x69
#-----------------------------------------------------------
sub parseType64Item {
	my $data = shift;
	my %item = ();
	
	$item{name} = substr($data,4,length($data) - 4);
	$item{name} =~ s/\x00//g;
	
	return %item;
}
#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseURIEntry {
	my $data = shift;
	my %item = ();
	$item{type} = unpack("C",substr($data,2,1)); 	
	
	my ($lo,$hi) = unpack("VV",substr($data,0x0e,8));
	$item{uritime} = ::getTime($lo,$hi);
	
	my $sz = unpack("V",substr($data,0x2a,4));
	my $uri = substr($data,0x2e,$sz);
	$uri =~ s/\x00//g;
	
	my $proto = substr($data,length($data) - 6, 6);
	$proto =~ s/\x00//g;
	
	$item{name} = $proto."://".$uri." [".gmtime($item{uritime})."]";
	
	return %item;
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseSystemFolderEntry {
	my $data     = shift;
	my %item = ();
	
	my %vals = (0x00 => "Explorer",
	            0x42 => "Libraries",
	            0x44 => "Users",
	            0x4c => "Public",
	            0x48 => "My Documents",
	            0x50 => "My Computer",
	            0x58 => "My Network Places",
	            0x60 => "Recycle Bin",
	            0x68 => "Explorer",
	            0x70 => "Control Panel",
	            0x78 => "Recycle Bin",
	            0x80 => "My Games");
	
	$item{type} = unpack("C",substr($data,2,1));
	$item{id}   = unpack("C",substr($data,3,1));
	if (exists $vals{$item{id}}) {
		$item{name} = $vals{$item{id}};
	}
	else {
		$item{name} = parseGUID(substr($data,4,16));
	}
	return %item;
}

#-----------------------------------------------------------
# parseGUID()
# Takes 16 bytes of binary data, returns a string formatted
# as an MS GUID.
#-----------------------------------------------------------
sub parseGUID {
	my $data     = shift;
  my $d1 = unpack("V",substr($data,0,4));
  my $d2 = unpack("v",substr($data,4,2));
  my $d3 = unpack("v",substr($data,6,2));
	my $d4 = unpack("H*",substr($data,8,2));
  my $d5 = unpack("H*",substr($data,10,6));
  my $guid = sprintf "{%08x-%04x-%04x-$d4-$d5}",$d1,$d2,$d3;
  
  if (exists $guids{$guid}) {
  	return "CLSID_".$guids{$guid};
  }
  else {
  	return $guid;
  }
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseDeviceEntry {
	my $data = shift;
	my %item = ();

	my $ofs = unpack("v",substr($data,4,2));
	my $tag = unpack("V",substr($data,6,4));
	
	if ($tag == 0) {
		my $guid1 = parseGUID(substr($data,$ofs + 6,16));
		my $guid2 = parseGUID(substr($data,$ofs + 6 + 16,16));
		$item{name} = $guid1."\\".$guid2
	}
	elsif ($tag == 2) {
		$item{name} = substr($data,0x0a,($ofs + 6) - 0x0a);
		$item{name} =~ s/\x00//g;
	}
	else {
    my $ver = unpack("C",substr($data,9,1));

# Version 3 = XP    
    if ($ver == 3) {
        if (length($data) > ($ofs + 6)) {
    	    my $guid1 = parseGUID(substr($data,$ofs + 6,16));
			my $guid2 = parseGUID(substr($data,$ofs + 6 + 16,16));
			$item{name} = $guid1."\\".$guid2
        }
    }
# Version 8 = Win7    
    elsif ($ver == 8) {
    	my $userlen = unpack("V",substr($data,30,4));
			my $devlen  = unpack("V",substr($data,34,4));
			my $user    = substr($data,0x28,$userlen * 2);
			$user =~ s/\x00//g;
			my $dev = substr($data,0x28 + ($userlen * 2),$devlen * 2);
			$dev =~ s/\x00//g;
			$item{name} = $user;	
    }
    else {
# Version unknown
    	
    }
	}
	return %item;
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseDriveEntry {
	my $data     = shift;
	my %item = ();
	$item{type} = unpack("C",substr($data,2,1));;
	$item{name} = substr($data,3,3);
	return %item;
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseControlPanelEntry {
	my $data     = shift;
	my %item = ();
	$item{type} = unpack("C",substr($data,2,1));
	my $guid = parseGUID(substr($data,14,16));
	if (exists $guids{$guid}) {
		$item{name} = $guids{$guid};
	}
	else {
		$item{name} = $guid;
	}
	return %item;
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseFolderEntry {
	my $data     = shift;
    my $data_length = length($data);
	my %item = ();
	
	$item{type} = unpack("C",substr($data,2,1));
# Type 0x74 folders have a slightly different format	
	
	my $ofs_mdate;
	my $ofs_shortname;
	
	if ($item{type} == 0x74) {
		$ofs_mdate = 0x12;
	}
	elsif (substr($data,4,4) eq "AugM") {
		$ofs_mdate = 0x1c;
	}
	else {
		$ofs_mdate = 0x08;
	}
# some type 0x32 items will include a file size	
	if ($item{type} == 0x32) {
		my $size = unpack("V",substr($data,4,4));
		if ($size != 0) {
			$item{filesize} = $size;
		}
	}
	
	my @m = unpack("vv",substr($data,$ofs_mdate,4));
	($item{mtime_str},$item{mtime}) = convertDOSDate($m[0],$m[1]);
	
# Need to read in short name; nul-term ASCII
#	$item{shortname} = (split(/\x00/,substr($data,12,length($data) - 12),2))[0];
	$ofs_shortname = $ofs_mdate + 6;	
	my $tag = 1;
	my $cnt = 0;
	my $str = "";
	while($tag) {
		my $s = substr($data,$ofs_shortname + $cnt,1);
		if ($s =~ m/\x00/ && ((($cnt + 1) % 2) == 0)) {
			$tag = 0;
		}
		else {
			$str .= $s;
			$cnt++;
		}
	}
#	$str =~ s/\x00//g;
	my $shortname = $str;
	my $ofs = $ofs_shortname + $cnt + 1;

# Read progressively, 1 byte at a time, looking for 0xbeef	
	$tag = 1;
	$cnt = 0;
	while ($tag) {
		if (unpack("v",substr($data,$ofs + $cnt,2)) == 0xbeef) {
			$tag = 0;
		}
		else {
			$cnt++;
            if (($ofs + $cnt) > $data_length) {
                return %item;
            }
		}
	}
	$item{extver} = unpack("v",substr($data,$ofs + $cnt - 4,2));
	$ofs = $ofs + $cnt + 2;
	
	@m = unpack("vv",substr($data,$ofs,4));
	($item{ctime_str},$item{ctime}) = convertDOSDate($m[0],$m[1]);
	$ofs += 4;
	@m = unpack("vv",substr($data,$ofs,4));
	($item{atime_str},$item{atime}) = convertDOSDate($m[0],$m[1]);
	
	my $jmp;
	if ($item{extver} == 0x03) {
		$jmp = 8;
	}
	elsif ($item{extver} == 0x07) {
		$jmp = 26;
	}
	elsif ($item{extver} == 0x08) {
		$jmp = 30;
	}
	else {}
	
	if ($item{extver} >= 0x07) {
		my @n = unpack("Vvv",substr($data,$ofs + 8, 8));
		$item{mft_rec_num} = getNum48($n[0],$n[1]);
		$item{mft_seq_num} = $n[2];
	}
	
	$ofs += $jmp;
	
	$str = substr($data,$ofs,length($data) - 30);
	my $longname = (split(/\x00\x00/,$str,2))[0];
	$longname =~ s/\x00//g;
	
	if ($longname ne "") {
		$item{name} = Utf16ToUtf8($longname);
	}
	else {
		$item{name} = Utf16ToUtf8($shortname);
	}
	return %item;
}

#-----------------------------------------------------------
# convertDOSDate()
# subroutine to convert 4 bytes of binary data into a human-
# readable format.  Returns both a string and a Unix-epoch
# time.
#-----------------------------------------------------------
sub convertDOSDate {
	my $date = shift;
	my $time = shift;
	
	if ($date == 0x00 || $time == 0x00){
		return (0,0);
	}
	else {
		my $sec = ($time & 0x1f) * 2;
		$sec = "0".$sec if (length($sec) == 1);
		if ($sec == 60) {$sec = 59};
		my $min = ($time & 0x7e0) >> 5;
		$min = "0".$min if (length($min) == 1);
		my $hr  = ($time & 0xF800) >> 11;
		$hr = "0".$hr if (length($hr) == 1);
		my $day = ($date & 0x1f);
		$day = "0".$day if (length($day) == 1);
		my $mon = ($date & 0x1e0) >> 5;
		$mon = "0".$mon if (length($mon) == 1);
		my $yr  = (($date & 0xfe00) >> 9) + 1980;
		my $gmtime = timegm($sec,$min,$hr,$day,($mon - 1),$yr);
    return ("$yr-$mon-$day $hr:$min:$sec",$gmtime);
#		return gmtime(timegm($sec,$min,$hr,$day,($mon - 1),$yr));
	}
}

#-----------------------------------------------------------
# parseFolderEntry2()
#
# Initial code for parsing type 0x35 
#-----------------------------------------------------------
sub parseFolderEntry2 {
	my $data     = shift;
	my %item = ();
	
	my $ofs = 0;
	my $tag = 1;

	while ($tag) {
		if (unpack("v",substr($data,$ofs,2)) == 0xbeef) {
			$tag = 0;
		}
		else {
			$ofs++;
		}
	}
	$item{extver} = unpack("v",substr($data,$ofs - 4,2));
# Move offset over to end of where the ctime value would be
	$ofs += 4;
	
	my $jmp;
	if ($item{extver} == 0x03) {
		$jmp = 8;
	}
	elsif ($item{extver} == 0x07) {
		$jmp = 26;
	}
	elsif ($item{extver} == 0x08) {
		$jmp = 30;
	}
	else {}
	
	$ofs += $jmp;
	
	my $str = substr($data,$ofs,length($data) - 30);
		
	$item{name} = (split(/\x00\x00/,$str,2))[0];
	$item{name} =~ s/\x13\x20/\x2D\x00/;
	$item{name} = Utf16ToUtf8($item{name});
	
	return %item;
}
#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseFolderEntry3 {
	my $data = shift;
	my %item = ();
	$item{name} = substr($data,4,length($data) - 4);
	$item{name} =~ s/\x00//g;
	return %item;
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseDatePathItem {
	my $data = shift;
	my %item = ();
	$item{datestr} = substr($data,0x18,30);
	my ($file,$dir) = split(/\x00\x00/,substr($data,0x44,length($data) - 0x44));
	$file =~ s/\x00//g;
	$dir =~ s/\x00//g;
	$item{name} = $dir.$file;
	return %item;	
}

#-----------------------------------------------------------
# parseTypex53()
#-----------------------------------------------------------
sub parseTypex53 {
	my $data = shift;
	my %item = ();
	
	my $item1 = parseGUID(substr($data,0x14,16));
	my $item2 = parseGUID(substr($data,0x24,16));
	
	$item{name} = $item1."\\".$item2;
	
	return %item;
}

#-----------------------------------------------------------
# probe()
#
# Code the uses printData() to insert a 'probe' into a specific
# location and display the data
#
# Input: binary data of arbitrary length
# Output: Nothing, no return value.  Displays data to the console
#-----------------------------------------------------------
sub probe {
	my $data = shift;
	my @d = printData($data);
	::rptMsg("");
	foreach (0..(scalar(@d) - 1)) {
		::rptMsg($d[$_]);
	}
	::rptMsg("");	
}

#-----------------------------------------------------------
# printData()
# subroutine used primarily for debugging; takes an arbitrary
# length of binary data, prints it out in hex editor-style
# format for easy debugging
#
# Usage: see probe()
#-----------------------------------------------------------
sub printData {
	my $data = shift;
	my $len = length($data);
	
	my @display = ();
	
	my $loop = $len/16;
	$loop++ if ($len%16);
	
	foreach my $cnt (0..($loop - 1)) {
# How much is left?
		my $left = $len - ($cnt * 16);
		
		my $n;
		($left < 16) ? ($n = $left) : ($n = 16);

		my $seg = substr($data,$cnt * 16,$n);
		my $lhs = "";
		my $rhs = "";
		foreach my $i ($seg =~ m/./gs) {
# This loop is to process each character at a time.
			$lhs .= sprintf(" %02X",ord($i));
			if ($i =~ m/[ -~]/) {
				$rhs .= $i;
    	}
    	else {
				$rhs .= ".";
     	}
		}
		$display[$cnt] = sprintf("0x%08X  %-50s %s",$cnt,$lhs,$rhs);
	}
	return @display;
}

#-----------------------------------------------------------
# getNum48()
# borrowed from David Cowen's code
#-----------------------------------------------------------
sub getNum48 {
	my $n1 = shift;
	my $n2 = shift;
	if ($n2 == 0) {
		return $n1;
	}
	else {
		$n2 = ($n2 *16777216);
		return $n1 + $n2;
	}
}

#---------------------------------------------------------------------
# Utf16ToUtf8()
#---------------------------------------------------------------------
sub Utf16ToUtf8 {
  my $str = $_[0];
  Encode::from_to($str,'UTF-16LE','utf8');
  $str = Encode::decode_utf8($str);
  return $str;
}

1;
