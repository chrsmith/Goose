#include "gtest/gtest.h"

#include "klib/argaccumulator.h"
#include "klib/limits.h"
#include "klib/type_printer.h"
#include "klib/types.h"

namespace klib {

// TODO(chris): Test that strings longer than kBufferSize get truncated.
TEST(StringPrinter, Default) {
  StringPrinter p;
  IOutputFn* p_as_outputfn = &p;

  EXPECT_STREQ("", p.Get());
  p.Print('1');
  p.Print('2');
  EXPECT_STREQ("12", p.Get());
  p_as_outputfn->Print("345");
  EXPECT_STREQ("12345", p.Get());

  p.Reset();
  EXPECT_STREQ("", p.Get());
  p_as_outputfn->Print("678");
  EXPECT_STREQ("678", p.Get());
}

TEST(StringPrinter, MaxSize) {
  StringPrinter p;
  IOutputFn* p_as_outputfn = &p;

  p_as_outputfn->Print("123456");
  EXPECT_STREQ("123456", p.Get());

  p.SetMaxSize(3);
  EXPECT_STREQ("123", p.Get());

  p.Print('4');  // Should get truncated.
  p.Print('5');  // Should get truncated.
  EXPECT_STREQ("123", p.Get());

  p.SetMaxSize(10);
  p.Print('X');
  p.Print('Y');
  EXPECT_STREQ("123XY", p.Get());
}

TEST(TypePrinter, Default) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintDefault(Arg::Of(uint32(42)));
  tp.PrintDefault(Arg::Of('x'));
  tp.PrintDefault(Arg::Of("str"));
  tp.PrintDefault(Arg::Of(uint64(1024)));
  EXPECT_STREQ(p.Get(), "42xstr0x0000000000000400");
}

TEST(TypePrinter, Char) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintChar(Arg::Of('['));
  tp.PrintChar(Arg::Of('\n'));
  tp.PrintChar(Arg::Of('\t'));
  tp.PrintChar(Arg::Of(' '));
  tp.PrintChar(Arg::Of('@'));
  tp.PrintChar(Arg::Of(']'));
  EXPECT_STREQ(p.Get(), "[\n\t @]");
}


TEST(TypePrinter, MaxInt32s) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintString(Arg::Of("max:"));
  tp.PrintDec(Arg::Of(kMaxInt32));
  tp.PrintString(Arg::Of(" min:"));
  tp.PrintDec(Arg::Of(kMinInt32));
  EXPECT_STREQ(p.Get(), "max:2147483647 min:-2147483648");
}

TEST(TypePrinter, MaxUInt32s) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintString(Arg::Of("max:"));
  tp.PrintDec(Arg::Of(kMaxUInt32));
  tp.PrintString(Arg::Of(" min:"));
  tp.PrintDec(Arg::Of(kMinUInt32));
  EXPECT_STREQ(p.Get(), "max:4294967295 min:0");
}

TEST(TypePrinter, HexInt32s) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintHex(Arg::Of(kMaxInt32));
  tp.PrintChar(Arg::Of(' '));
  tp.PrintHex(Arg::Of(kMaxUInt32));
  tp.PrintChar(Arg::Of(' '));
  tp.PrintHex(Arg::Of(0));
  EXPECT_STREQ(p.Get(), "0x7FFFFFFF 0xFFFFFFFF 0x00000000");
}

TEST(TypePrinter, HexInt64s) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintHex(Arg::Of(kMaxInt64));
  tp.PrintChar(Arg::Of(' '));
  tp.PrintHex(Arg::Of(kMinInt64));
  tp.PrintChar(Arg::Of(' '));
  tp.PrintHex(Arg::Of(kMaxUInt64));
  EXPECT_STREQ(
      p.Get(), "0x7FFFFFFFFFFFFFFF 0x8000000000000000 0xFFFFFFFFFFFFFFFF");
}

TEST(TypePrinter, BinUInt32s) {
  StringPrinter p;
  TypePrinter tp(&p);

  p.Reset();
  tp.PrintBin(Arg::Of(0x12344321U));
  EXPECT_STREQ(p.Get(), "0b00010010001101000100001100100001");

  p.Reset();
  tp.PrintBin(Arg::Of(kMaxUInt32));
  EXPECT_STREQ(p.Get(), "0b11111111111111111111111111111111");

  p.Reset();
  tp.PrintBin(Arg::Of(0U));
  EXPECT_STREQ(p.Get(), "0b00000000000000000000000000000000");
}

TEST(TypePrinter, String) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintString(Arg::Of("alpha beta gamma"));
  EXPECT_STREQ(p.Get(), "alpha beta gamma");
}

TEST(TypePrinter, HexError) {
  StringPrinter p;
  TypePrinter tp(&p);

  tp.PrintHex(Arg::Of("a cstr"));
  EXPECT_STREQ(p.Get(), "[ERROR: Invalid type]");
}

}
