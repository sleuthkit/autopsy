#-----------------------------------------------------------
# shimcache_tln.pl
# Variant of the appcompatcache.pl plugin, meant to parse data from
# all available ControlSets; this is meant as a test to see how it 
# works within an analysis process.
#
# History:
#  20190112 - updated parsing for Win8.1
#  20180311 - updated for more recent version of Win10/Win2016
#  20160528 - created
#
# References:
#  https://binaryforay.blogspot.com/2016/05/appcompatcacheparser-v0900-released-and.html
#  Blog post: https://blog.mandiant.com/archives/2459
#  Whitepaper: http://fred.mandiant.com/Whitepaper_ShimCacheParser.pdf
#  Tool: https://github.com/mandiant/ShimCacheParser
#  Win10: http://binaryforay.blogspot.com/2015/04/appcompatcache-changes-in-windows-10.html
#
# This plugin is based solely on the work and examples provided by Mandiant;
# thanks to them for sharing this information, and making the plugin possible.
# 
# copyright 2016 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shimcache_tln;
use strict;

my %config = (hive          => "System",
							hivemask      => 4,
							output        => "tln",
							category      => "Program Execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31,  
              version       => 20190112);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse file refs from System hive AppCompatCache data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shimcache_tln v.".$VERSION);
#	::rptMsg("shimcache_tln v.".$VERSION); 
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my @sets = ();
	
	my @subkeys = ();
	if (@subkeys = $root_key->get_list_of_subkeys()) {
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
			push(@sets,$name) if ($name =~ m/^ControlSet/);
		}
	}
	
	my $set;
	foreach $set (@sets) {
#		::rptMsg("*** ".$set." ***");
		my $appcompat_path = $set."\\Control\\Session Manager";
		my $appcompat;
		if ($appcompat = $root_key->get_subkey($appcompat_path)) {
#			::rptMsg($appcompat_path);
#			::rptMsg("LastWrite Time: ".gmtime($appcompat->get_timestamp())." Z");
			my $app_data = 0;
			%files = ();
			
			eval {
				$app_data = $appcompat->get_subkey("AppCompatibility")->get_value("AppCompatCache")->get_data();
#				::rptMsg($appcompat_path."\\AppCompatibility");
#			  ::rptMsg("LastWrite Time: ".gmtime($appcompat->get_subkey("AppCompatibility")->get_timestamp())." Z");
			};
			
			eval {
				$app_data = $appcompat->get_subkey("AppCompatCache")->get_value("AppCompatCache")->get_data();
#				::rptMsg($appcompat_path."\\AppCompatCache");
#			  ::rptMsg("LastWrite Time: ".gmtime($appcompat->get_subkey("AppCompatCache")->get_timestamp())." Z");
			};
			
			my $sig = unpack("V",substr($app_data,0,4));
#			::rptMsg(sprintf "Signature: 0x%x",$sig);
			
			if ($sig == 0xdeadbeef) {
				eval {
					appXP32Bit($app_data);
				};
			}
			elsif ($sig == 0xbadc0ffe) {
				eval {
					appWin2k3($app_data);
				};
			}
			elsif ($sig == 0xbadc0fee) {
				eval {
					appWin7($app_data);
				};
			
			}
			elsif ($sig == 0x80) {
				appWin8($app_data);				
			}
			elsif ($sig == 0x0) {
				appWin81($app_data);
			}
			elsif ($sig == 0x30 || $sig == 0x34) {
				appWin10($app_data);				
			}
			else {
				::rptMsg(sprintf "Unknown signature: 0x%x",$sig);
			}

			foreach my $f (keys %files) {
				my $str;
				next if ($files{$f}{modtime} == 0);
				if (exists $files{$f}{updtime}) {
#					$str = "[Program Execution] AppCompatCache - ".$files{$f}{filename};
					next if ($files{$f}{updtime} == 0);
          ::rptMsg($files{$f}{updtime}."|REG|||[Program Execution] - ".$files{$f}{filename});
				}
				
				$str = "M... AppCompatCache - ".$files{$f}{filename};
				$str .= " [Size = ".$files{$f}{size}." bytes]" if (exists $files{$f}{size});

				::rptMsg($files{$f}{modtime}."|REG|||".$str);
			}
		}
		else {
			::rptMsg($appcompat_path." not found.");
		}
#		::rptMsg("");
	}
}

#-----------------------------------------------------------
# appXP32Bit()
# parse 32-bit XP data
#-----------------------------------------------------------
sub appXP32Bit {
	my $data = shift;
#	::rptMsg("WinXP, 32-bit");
# header is 400 bytes; each structure is 552 bytes in size
	my $num_entries = unpack("V",substr($data,4,4));
	
	foreach my $i (0..($num_entries - 1)) {
		my $x = substr($data,(400 + ($i * 552)),552);
		my $file = (split(/\00\00/,substr($x,0,488)))[0];
		$file =~ s/\00//g;
		$file =~ s/^\\\?\?\\//;
		my ($mod1,$mod2) = unpack("VV",substr($x,528,8));
		my $modtime      = ::getTime($mod1,$mod2);
		my ($sz1,$sz2)   = unpack("VV",substr($x,536,8));
		my $sz;
		($sz2 == 0)?($sz = $sz1):($sz = "Too big");
		my ($up1,$up2)   = unpack("VV",substr($x,544,8));
		my $updtime      = ::getTime($up1,$up2);
		$files{$i}{filename} = $file;
		$files{$i}{size} = $sz;
		$files{$i}{modtime} = $modtime;
		$files{$i}{updtime} = $updtime;
	}
}
#-----------------------------------------------------------
# appWin2k3()
# parse Win2k3, Vista, Win2k8 data
#-----------------------------------------------------------
sub appWin2k3 {
	my $data = shift;
	my $num_entries = unpack("V",substr($data,4,4));
#	::rptMsg("Num_entries: ".$num_entries);
	my $struct_sz = 0;
	my ($len,$max_len,$padding) = unpack("vvV",substr($data,8,8));
	if (($max_len - $len) == 2) {
# if $padding == 0, 64-bit; otherwise, 32-bit
		if ($padding == 0) {
			$struct_sz = 32;
#			::rptMsg("Win2K3/Vista/Win2K8, 64-bit");
		}
		else {
			$struct_sz = 24;
#			::rptMsg("Win2K3/Vista/Win2K8, 32-bit");
		}
	}
	
	foreach my $i (0..($num_entries - 1)) {
		my $struct = substr($data,(8 + ($struct_sz * $i)),$struct_sz);
		if ($struct_sz == 24) {
			my ($len,$max_len,$ofs,$t0,$t1,$f0,$f1) = unpack("vvVVVVV",$struct);
			
			my $file = substr($data,$ofs,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
			$files{$i}{filename} = $file;
			$files{$i}{modtime} = $t;
#			$files{$file}{size} = $f0 if (($f1 == 0) && ($f0 > 3));
			$files{$i}{executed} = 1 if (($f0 < 4) && ($f0 & 0x2));
		}
		elsif ($struct_sz == 32) {
			my ($len,$max_len,$padding,$ofs0,$ofs1,$t0,$t1,$f0,$f1) = unpack("vvVVVVVVV",$struct);
			my $file = substr($data,$ofs0,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
			$files{i}{filename} = $file;
			$files{$i}{modtime} = $t;
			$files{$i}{size} = $f0 if (($f1 == 0) && ($f0 > 3));
			$files{$i}{executed} = 1 if (($f0 < 4) && ($f0 & 0x2));
		}
		else {
#
		}
	}
}

#-----------------------------------------------------------
# appWin7()
# parse Win2k8R2, Win7 data
#-----------------------------------------------------------
sub appWin7 {
	my $data = shift;
	my $struct_sz = 0;
	my $num_entries = unpack("V",substr($data,4,4));
#	::rptMsg("Num_entries: ".$num_entries);
#	::rptMsg("Data Length: ".length($data)." bytes");
# 128-byte header	
	my ($len,$max_len,$padding) = unpack("vvV",substr($data,128,8));
	if (($max_len - $len) == 2) {
		if ($padding == 0) {
			$struct_sz = 48;
#			::rptMsg("Win2K8R2/Win7, 64-bit");
		}
		else {
			$struct_sz = 32;
#			::rptMsg("Win2K8R2/Win7, 32-bit");
		}
	}

	foreach my $i (0..($num_entries - 1)) {
		my $struct = substr($data,(128 + ($struct_sz * $i)),$struct_sz);
		if ($struct_sz == 32) {
			my ($len,$max_len,$ofs,$t0,$t1,$f0,$f1) = unpack("vvV5x8",$struct);
			my $file = substr($data,$ofs,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
 			$files{$i}{filename} = $file;	
			$files{$i}{modtime} = $t;
			$files{$i}{executed} = 1 if ($f0 & 0x2);
		}
		else {
			my ($len,$max_len,$padding,$ofs0,$ofs1,$t0,$t1,$f0,$f1) = unpack("vvV7x16",$struct);
			my $file = substr($data,$ofs0,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
 			$files{$i}{filename} = $file;	
			$files{$i}{modtime} = $t;
			$files{$i}{executed} = 1 if ($f0 & 0x2);
		}
	}
}

#-----------------------------------------------------------
# appWin8()
#-----------------------------------------------------------
sub appWin8 {
	my $data = shift;
	my $len = length($data);
	my ($jmp, $t0, $t1, $sz, $name);
	my $ct = 0;
	my $ofs = unpack("V",substr($data,0,4));
	
	while($ofs < $len) {
		my $tag = unpack("V",substr($data,$ofs,4));
        last unless (defined $tag);
# 32-bit		
		if ($tag == 0x73746f72) {
			$jmp = unpack("V",substr($data,$ofs + 8,4));
			($t0,$t1) = unpack("VV",substr($data,$ofs + 12,8));
			$sz = unpack("v",substr($data,$ofs + 20,2));
			$name = substr($data,$ofs + 22,$sz);
			$name =~ s/\00//g;
			$files{$ct}{filename} = $name;
			$files{$ct}{modtime} = ::getTime($t0,$t1);
			$ct++;
			$ofs += ($jmp + 12);
		}
# 64-bit
		elsif ($tag == 0x73743030 || $tag == 0x73743031) {
			$jmp = unpack("V",substr($data,$ofs + 8,4));
			$sz = unpack("v",substr($data,$ofs + 0x0C,2));
			$name = substr($data,$ofs + 0x0E,$sz + 2);
			$name =~ s/\00//g;
			($t0,$t1) = unpack("VV",substr($data,($ofs + 0x0E + $sz +2 + 8),8));
			$files{$ct}{filename} = $name;
			$files{$ct}{modtime} = ::getTime($t0,$t1);
			$ct++;
			$ofs += ($jmp + 12);
		}		
		else {
# Unknown tag
		}			
	
	}
}

#-----------------------------------------------------------
# appWin81()
# 
#-----------------------------------------------------------
sub appWin81 {
	my $data = shift;
	my $len = length($data);
	my ($tag, $sz, $t0, $t1, $name, $name_len);
	my $ct = 0;
#	my $ofs = unpack("V",substr($data,0,4));
	my $ofs = 0x80;
	
	while ($ofs < $len) {
		$tag = substr($data,$ofs,4);
        last unless (defined $tag);
		if ($tag eq "10ts") {
			
			$sz = unpack("V",substr($data,$ofs + 0x08,4));
			$name_len   = unpack("v",substr($data,$ofs + 0x0c,2));
			my $name      = substr($data,$ofs + 0x0e,$name_len);
			$name =~ s/\00//g;
#			($t0,$t1) = unpack("VV",substr($data,$ofs + 0x03 + $name_len,8));
			($t0,$t1) = unpack("VV",substr($data,$ofs + 0x0e + $name_len + 0x0a,8));
			$files{$ct}{filename} = $name;
			$files{$ct}{modtime} = ::getTime($t0,$t1);

			$ct++;
			$ofs += ($sz + 0x0c);
		}
	}
}


#-----------------------------------------------------------
# appWin10()
# Ref: http://binaryforay.blogspot.com/2015/04/appcompatcache-changes-in-windows-10.html
#-----------------------------------------------------------
sub appWin10 {
	my $data = shift;
	my $len = length($data);
	my ($tag, $sz, $t0, $t1, $name, $name_len);
	my $ct = 0;
	my $ofs = unpack("V",substr($data,0,4));
#	my $ofs = 0x30;
	
	while ($ofs < $len) {
		$tag = substr($data,$ofs,4);
		if ($tag eq "10ts") {
			
			$sz = unpack("V",substr($data,$ofs + 0x08,4));
			$name_len   = unpack("v",substr($data,$ofs + 0x0c,2));
			my $name      = substr($data,$ofs + 0x0e,$name_len);
			$name =~ s/\00//g;
#			($t0,$t1) = unpack("VV",substr($data,$ofs + 0x03 + $name_len,8));
			($t0,$t1) = unpack("VV",substr($data,$ofs + 0x0e + $name_len,8));
			$files{$ct}{filename} = $name;
			$files{$ct}{modtime} = ::getTime($t0,$t1);
			$ct++;
			$ofs += ($sz + 0x0c);
		}
	}
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
	
	foreach (0..(scalar(@d) - 1)) {
		print $d[$_]."\n";
	}
}

#-----------------------------------------------------------
# printData()
# subroutine used primarily for debugging; takes an arbitrary
# length of binary data, prints it out in hex editor-style
# format for easy debugging
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
1;
