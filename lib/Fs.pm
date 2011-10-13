#
package Fs;

$Fs::types[0]  = "ext";
$Fs::types[1]  = "fat";
$Fs::types[2]  = "ntfs";
$Fs::types[3]  = "ufs";
$Fs::types[4]  = "iso9660";
$Fs::types[5]  = "-----";
$Fs::types[6]  = "fat12";
$Fs::types[7]  = "fat16";
$Fs::types[8]  = "fat32";
$Fs::types[9]  = "bsdi";
$Fs::types[10] = "freebsd";
$Fs::types[11] = "openbsd";
$Fs::types[12] = "solaris";
$Fs::types[13] = "hfs";

# These need to be updated as The Sleuth Kit supports more file systems
#
# addr_unit contains the addressable unit per filesystem type
# first_meta contains the first usable meta address on a system
# root_meta is the meta address for the root directory (diff than
# first on ntfs)

$Fs::addr_unit{'disk'}  = 'Sector';
$Fs::first_addr{'disk'} = 0;
$Fs::is_fs{'disk'}      = 0;

$Fs::addr_unit{'blkls'}  = 'Unit';
$Fs::first_addr{'blkls'} = 0;
$Fs::is_fs{'blkls'}      = 0;

# raw
$Fs::addr_unit{'raw'}  = 'Unit';
$Fs::first_addr{'raw'} = 0;
$Fs::is_fs{'raw'}      = 0;

# Swap
$Fs::addr_unit{'swap'}  = 'Unit';
$Fs::first_addr{'swap'} = 0;
$Fs::is_fs{'swap'}      = 0;

# BSDI
$Fs::first_meta{'bsdi'} = $Fs::root_meta{'bsdi'} = 2;
$Fs::first_addr{'bsdi'} = 0;
$Fs::addr_unit{'bsdi'}  = 'Fragment';
$Fs::has_ctime{'bsdi'}  = 1;
$Fs::has_crtime{'bsdi'} = 0;
$Fs::has_mtime{'bsdi'}  = 1;
$Fs::meta_str{'bsdi'}   = "Inode";
$Fs::is_fs{'bsdi'}      = 1;

# FreeBSD
$Fs::first_meta{'freebsd'} = $Fs::root_meta{'freebsd'} = 2;
$Fs::first_addr{'freebsd'} = 0;
$Fs::addr_unit{'freebsd'}  = 'Fragment';
$Fs::has_ctime{'freebsd'}  = 1;
$Fs::has_crtime{'freebsd'} = 0;
$Fs::has_mtime{'freebsd'}  = 1;
$Fs::meta_str{'freebsd'}   = "Inode";
$Fs::is_fs{'freebsd'}      = 1;

# OpenBSD
$Fs::first_meta{'openbsd'} = $Fs::root_meta{'openbsd'} = 2;
$Fs::first_addr{'openbsd'} = 0;
$Fs::addr_unit{'openbsd'}  = 'Fragment';
$Fs::has_ctime{'openbsd'}  = 1;
$Fs::has_crtime{'openbsd'} = 0;
$Fs::has_mtime{'openbsd'}  = 1;
$Fs::meta_str{'openbsd'}   = "Inode";
$Fs::is_fs{'openbsd'}      = 1;

# Solaris
$Fs::first_meta{'solaris'} = $Fs::root_meta{'solaris'} = 2;
$Fs::first_addr{'solaris'} = 0;
$Fs::addr_unit{'solaris'}  = 'Fragment';
$Fs::has_ctime{'solaris'}  = 1;
$Fs::has_crtime{'solaris'} = 0;
$Fs::has_mtime{'solaris'}  = 1;
$Fs::meta_str{'solaris'}   = "Inode";
$Fs::is_fs{'solaris'}      = 1;

# UFS
$Fs::first_meta{'ufs'} = $Fs::root_meta{'ufs'} = 2;
$Fs::first_addr{'ufs'} = 0;
$Fs::addr_unit{'ufs'}  = 'Fragment';
$Fs::has_ctime{'ufs'}  = 1;
$Fs::has_crtime{'ufs'} = 0;
$Fs::has_mtime{'ufs'}  = 1;
$Fs::meta_str{'ufs'}   = "Inode";
$Fs::is_fs{'ufs'}      = 1;

# Linux
$Fs::first_meta{'linux-ext2'} = $Fs::root_meta{'linux-ext2'} = 2;
$Fs::first_addr{'linux-ext2'} = 0;
$Fs::addr_unit{'linux-ext2'}  = 'Fragment';
$Fs::has_ctime{'linux-ext2'}  = 1;
$Fs::has_crtime{'linux-ext2'} = 0;
$Fs::has_mtime{'linux-ext2'}  = 1;
$Fs::meta_str{'linux-ext2'}   = "Inode";
$Fs::is_fs{'linux-ext2'}      = 1;

$Fs::first_meta{'linux-ext3'} = $Fs::root_meta{'linux-ext3'} = 2;
$Fs::first_addr{'linux-ext3'} = 0;
$Fs::addr_unit{'linux-ext3'}  = 'Fragment';
$Fs::has_ctime{'linux-ext3'}  = 1;
$Fs::has_crtime{'linux-ext3'} = 0;
$Fs::has_mtime{'linux-ext3'}  = 1;
$Fs::meta_str{'linux-ext3'}   = "Inode";
$Fs::is_fs{'linux-ext3'}      = 1;

$Fs::first_meta{'ext'} = $Fs::root_meta{'ext'} = 2;
$Fs::first_addr{'ext'} = 0;
$Fs::addr_unit{'ext'}  = 'Fragment';
$Fs::has_ctime{'ext'}  = 1;
$Fs::has_crtime{'ext'} = 0;
$Fs::has_mtime{'ext'}  = 1;
$Fs::meta_str{'ext'}   = "Inode";
$Fs::is_fs{'ext'}      = 1;

# FAT
$Fs::first_meta{'fat'} = $Fs::first_meta{'fat12'} = $Fs::first_meta{'fat16'} =
  $Fs::first_meta{'fat32'} = 1;
$Fs::root_meta{'fat'}      = $Fs::root_meta{'fat12'} = $Fs::root_meta{'fat16'} =
  $Fs::root_meta{'fat32'}  = 2;
$Fs::first_addr{'fat'} = $Fs::first_addr{'fat12'} = $Fs::first_addr{'fat16'} =
  $Fs::first_addr{'fat32'} = 0;
$Fs::addr_unit{'fat'}      = $Fs::addr_unit{'fat12'} = $Fs::addr_unit{'fat16'} =
  $Fs::addr_unit{'fat32'}  = 'Sector';
$Fs::has_ctime{'fat'} = $Fs::has_ctime{'fat12'} = $Fs::has_ctime{'fat16'} =
  $Fs::has_ctime{'fat32'} = 0;
$Fs::has_crtime{'fat'} = $Fs::has_crtime{'fat12'} = $Fs::has_crtime{'fat16'} =
  $Fs::has_crtime{'fat32'} = 1;
$Fs::has_mtime{'fat'}      = $Fs::has_mtime{'fat12'} = $Fs::has_mtime{'fat16'} =
  $Fs::has_mtime{'fat32'}  = 1;
$Fs::meta_str{'fat'} = $Fs::meta_str{'fat12'} = $Fs::meta_str{'fat16'} =
  $Fs::meta_str{'fat32'} = "Dir Entry";
$Fs::is_fs{'fat'}        = $Fs::is_fs{'fat12'} = $Fs::is_fs{'fat16'} =
  $Fs::is_fs{'fat32'}    = 1;

# NTFS
$Fs::first_meta{'ntfs'} = 0;
$Fs::root_meta{'ntfs'}  = 5;
$Fs::first_addr{'ntfs'} = 0;
$Fs::addr_unit{'ntfs'}  = 'Cluster';
$Fs::has_ctime{'ntfs'}  = 1;
$Fs::has_crtime{'ntfs'} = 1;
$Fs::has_mtime{'ntfs'}  = 1;
$Fs::meta_str{'ntfs'}   = "MFT Entry";
$Fs::is_fs{'ntfs'}      = 1;

# ISO9660
$Fs::first_meta{'iso9660'} = $Fs::root_meta{'iso9660'} = 0;
$Fs::first_addr{'iso9660'} = 0;
$Fs::addr_unit{'iso9660'}  = 'Block';
$Fs::has_ctime{'iso9660'}  = 0;
$Fs::has_crtime{'iso9660'} = 1;
$Fs::has_mtime{'iso9660'}  = 0;
$Fs::meta_str{'iso9660'}   = "Directory Entry";
$Fs::is_fs{'iso9660'}      = 1;

# HFS
$Fs::first_meta{'hfs'} = $Fs::root_meta{'hfs'} = 2;
$Fs::first_addr{'hfs'} = 0;
$Fs::addr_unit{'hfs'}  = 'Block';
$Fs::has_ctime{'hfs'}  = 1;
$Fs::has_crtime{'hfs'} = 1;
$Fs::has_mtime{'hfs'}  = 1;
$Fs::meta_str{'hfs'}   = "Record";
$Fs::is_fs{'hfs'}      = 1;
