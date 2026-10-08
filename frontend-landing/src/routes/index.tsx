import { createFileRoute } from '@tanstack/react-router'
import { Hero } from '@/components/Hero'
import { Section2 } from '@/components/Section2'
import { Section3 } from '@/components/Section3'
import { Section4 } from '@/components/Section4'
import { Section5 } from '@/components/Section5'
import { Section6 } from '@/components/Section6'
import { Section7 } from '@/components/Section7'
import { Section8 } from '@/components/Section8'

export const Route = createFileRoute('/')({ component: Home })

function Home() {
  return (
    <main className="bg-[#1b1b1b] text-white font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
      <Hero />
      <Section2 />
      <Section3 />
      <Section4 />
      <Section5 />
      <Section6 />
      <Section7 />
      <Section8 />
    </main>
  )
}
