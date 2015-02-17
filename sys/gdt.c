// http://wiki.osdev.org/GDT_Tutorial

#include "sys/gdt.h"

#include "lib/types.h"

// The global descriptor table here is very basic. We only install three
// entries, a null segment, and others for code and data. Each overlapping
// and spanning the full 4GiB of addressable memory.
//
// http://en.wikipedia.org/wiki/Global_Descriptor_Table
// http://wiki.osdev.org/Global_Descriptor_Table
// http://www.osdever.net/bkerndev/Docs/gdt.htm

typedef struct __attribute__((packed)) {
  uint16_t limit_low;
  uint16_t base_low;
  uint8_t  base_middle;
  uint8_t access;
  uint8_t granularity;
  uint8_t base_high;
} GdtEntry;

typedef struct __attribute__((packed)) {
  uint16_t limit;
  uint32_t base;
} GdtPointer;

// Our GDT only has three entries. Initialized via gdt_install.
GdtPointer global_descriptor_table_ptr;
GdtEntry global_descriptor_table[3];

// Defined in gdt_asm.s, used to properly clear out the existing GDT (from the
// boot loader) and replace it with our own.
extern void gdt_flush();

// Setup a descriptor table entry.
void gdt_set_gate(GdtEntry* entry,
		  uint32_t base,
		  uint32_t limit,
		  uint8_t access,
		  uint8_t gran) {
  entry->base_low = (base & 0xFFFF);
  entry->base_middle = (base >> 16) & 0xFF;
  entry->base_high = (base >> 24) & 0xFF;

  entry->limit_low = (limit & 0xFFFF);
  entry->granularity = ((limit >> 16) & 0x0F);

  entry->granularity |= (gran & 0xF0);
  entry->access = access;
}

void gdt_install() {
  // Rely on sizeof(X[3]) == sizeof(X * 3).
  global_descriptor_table_ptr.limit = sizeof(global_descriptor_table) - 1;
  global_descriptor_table_ptr.base = (unsigned int) &global_descriptor_table;

  // null, code, and data segments. The base address is 0, and the limit is
  // 4GiB. Uses 32-bit opcodes.
  gdt_set_gate(&global_descriptor_table[0],
	       0, 0, 0, 0);
  gdt_set_gate(&global_descriptor_table[1],
	       0, 0xFFFFFFFF, 0x9A, 0xCF);
  gdt_set_gate(&global_descriptor_table[2],
	       0, 0xFFFFFFFF, 0x92, 0xCF);

  // Clear out the old one, we are now on our own.
  gdt_flush();
}
